package frc.robot.subsystems;

import com.ctre.phoenix6.StatusSignal;
import com.ctre.phoenix6.configs.CANcoderConfiguration;
import com.ctre.phoenix6.configs.MagnetSensorConfigs;
import com.ctre.phoenix6.hardware.CANcoder;
import com.ctre.phoenix6.signals.AbsoluteSensorRangeValue;
import com.ctre.phoenix6.signals.SensorDirectionValue;
import com.revrobotics.CANSparkBase.ControlType;
import com.revrobotics.CANSparkBase.IdleMode;
import com.revrobotics.CANSparkLowLevel.MotorType;
import com.revrobotics.CANSparkLowLevel.PeriodicFrame;
import com.revrobotics.CANSparkMax;
import com.revrobotics.REVLibError;
import com.revrobotics.REVPhysicsSim;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.SparkPIDController;
import com.revrobotics.SparkPIDController.ArbFFUnits;
import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.controller.SimpleMotorFeedforward;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.math.system.plant.DCMotor;
import edu.wpi.first.wpilibj.DataLogManager;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.RobotBase;
import edu.wpi.first.wpilibj.RobotController;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.simulation.DCMotorSim;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import frc.robot.Constants.SwerveConstants;
import frc.robot.util.Alert;
import frc.robot.util.Alert.AlertType;
import frc.robot.util.FaultLogger;
import frc.robot.util.SparkMaxUtil;
import java.util.function.Consumer;
import monologue.Annotations.Log;
import monologue.Logged;

public class SwerveModule implements Logged {
  @Log.NT.Once private String moduleName;

  private CANSparkMax driveMotor;
  private CANSparkMax turnMotor;
  private CANcoder canCoder;

  private RelativeEncoder driveEncoder;
  private RelativeEncoder turnEncoder;

  private SparkPIDController drivePID;
  private SparkPIDController turnPID;

  private SimpleMotorFeedforward driveFeedforward =
      new SimpleMotorFeedforward(
          SwerveConstants.driveKs, SwerveConstants.driveKv, SwerveConstants.driveKa);

  @Log.NT.Once private Rotation2d angleOffset;

  private StatusSignal<Double> absoluteAngleSignal;

  @Log.NT private boolean isOpenLoop;
  @Log.NT private boolean allowTurnInPlace;

  @Log.NT private SwerveModuleState desiredState = new SwerveModuleState();
  @Log.NT private SwerveModuleState previousState = new SwerveModuleState();

  @Log.NT private double characterizationVolts = 0.0;
  @Log.NT private boolean characterizing = false;

  private Alert noAbsoluteValue;
  private Alert motorPositionNotSet;

  private DCMotorSim driveSim = new DCMotorSim(DCMotor.getNEO(1), 6.12, 0.025);
  private DCMotorSim turnSim = new DCMotorSim(DCMotor.getNEO(1), 150.0 / 7.0, 0.004);

  private double simPosition = 0.0;
  private double simVelocity = 0.0;
  private double simAngle = 0.0;

  public SwerveModule(
      String moduleName, int driveID, int turnID, int canCoderID, Rotation2d angleOffset) {
    DataLogManager.log("[" + moduleName + "] Initializing");
    this.moduleName = moduleName;
    this.angleOffset = angleOffset;

    noAbsoluteValue =
        new Alert("No absolute angle received for " + moduleName + ".", AlertType.ERROR);
    motorPositionNotSet =
        new Alert("Turn motor position not set for " + moduleName + ".", AlertType.ERROR);

    driveMotor = new CANSparkMax(driveID, MotorType.kBrushless);
    turnMotor = new CANSparkMax(turnID, MotorType.kBrushless);
    canCoder = new CANcoder(canCoderID);

    driveEncoder = driveMotor.getEncoder();
    drivePID = driveMotor.getPIDController();

    turnEncoder = turnMotor.getEncoder();
    turnPID = turnMotor.getPIDController();

    Timer.delay(0.10);

    absoluteAngleSignal = canCoder.getAbsolutePosition();

    configureMotors();
    configureAngleEncoder();

    if (RobotBase.isSimulation()) {
      REVPhysicsSim.getInstance().addSparkMax(driveMotor, DCMotor.getNEO(1));
      REVPhysicsSim.getInstance().addSparkMax(turnMotor, DCMotor.getNEO(1));
    }

    DataLogManager.log(moduleName + " Drive Firmware: " + driveMotor.getFirmwareString());
    DataLogManager.log(moduleName + " Turn Firmware: " + turnMotor.getFirmwareString());

    FaultLogger.register(driveMotor);
    FaultLogger.register(turnMotor);
    DataLogManager.log("[" + moduleName + "] Initialization Complete");
  }

  private void configureMotors() {
    configureDriveMotor();
    configureTurnMotor();
  }

  private void configureDriveMotor() {
    DataLogManager.log("[" + moduleName + "] Configuring Drive Motor");
    SparkMaxUtil.configure(
        driveMotor,
        () -> driveMotor.clearFaults(),
        () -> SparkMaxUtil.setInverted(driveMotor, SwerveConstants.driveMotorInverted),
        () -> driveMotor.setOpenLoopRampRate(SwerveConstants.openLoopRamp),
        () -> driveMotor.setClosedLoopRampRate(SwerveConstants.closedLoopRamp),
        () -> driveMotor.enableVoltageCompensation(SwerveConstants.voltageCompensation),
        () -> driveMotor.setSmartCurrentLimit(SwerveConstants.driveCurrentLimit),
        () -> driveMotor.setIdleMode(IdleMode.kBrake),
        () -> drivePID.setP(SwerveConstants.driveP),
        () -> drivePID.setOutputRange(-1.0, 1.0),
        () -> driveEncoder.setPositionConversionFactor(SwerveConstants.drivePositionConversion),
        () -> driveEncoder.setVelocityConversionFactor(SwerveConstants.driveVelocityConversion),
        () -> driveMotor.setPeriodicFramePeriod(PeriodicFrame.kStatus0, 20),
        () -> driveMotor.setPeriodicFramePeriod(PeriodicFrame.kStatus1, 20),
        () ->
            driveMotor.setPeriodicFramePeriod(
                PeriodicFrame.kStatus2, 1000 / SwerveConstants.odometryUpdateFrequency),
        () ->
            driveMotor.setPeriodicFramePeriod(
                PeriodicFrame.kStatus3, SparkMaxUtil.disableFramePeriod),
        () ->
            driveMotor.setPeriodicFramePeriod(
                PeriodicFrame.kStatus4, SparkMaxUtil.disableFramePeriod),
        () ->
            driveMotor.setPeriodicFramePeriod(
                PeriodicFrame.kStatus5, SparkMaxUtil.disableFramePeriod),
        () ->
            driveMotor.setPeriodicFramePeriod(
                PeriodicFrame.kStatus6, SparkMaxUtil.disableFramePeriod),
        () ->
            driveMotor.setPeriodicFramePeriod(
                PeriodicFrame.kStatus7, SparkMaxUtil.disableFramePeriod));
  }

  private void configureTurnMotor() {
    DataLogManager.log("[" + moduleName + "] Configuring Turn Motor");
    SparkMaxUtil.configure(
        turnMotor,
        () -> turnMotor.clearFaults(),
        () -> SparkMaxUtil.setInverted(turnMotor, SwerveConstants.turnMotorInverted),
        () -> turnMotor.disableVoltageCompensation(),
        () -> turnMotor.setSmartCurrentLimit(SwerveConstants.turnCurrentLimit),
        () -> turnMotor.setIdleMode(IdleMode.kCoast),
        () -> turnEncoder.setPositionConversionFactor(SwerveConstants.turnPositionConversion),
        () -> turnPID.setP(SwerveConstants.turnP),
        () -> turnPID.setD(SwerveConstants.turnD),
        () -> turnPID.setOutputRange(-1.0, 1.0),
        () -> turnPID.setPositionPIDWrappingEnabled(true),
        () -> turnPID.setPositionPIDWrappingMinInput(-Math.PI),
        () -> turnPID.setPositionPIDWrappingMaxInput(Math.PI),
        () -> turnMotor.setPeriodicFramePeriod(PeriodicFrame.kStatus0, 20),
        () -> turnMotor.setPeriodicFramePeriod(PeriodicFrame.kStatus1, 100),
        () ->
            turnMotor.setPeriodicFramePeriod(
                PeriodicFrame.kStatus2, 1000 / SwerveConstants.odometryUpdateFrequency),
        () ->
            turnMotor.setPeriodicFramePeriod(
                PeriodicFrame.kStatus3, SparkMaxUtil.disableFramePeriod),
        () ->
            turnMotor.setPeriodicFramePeriod(
                PeriodicFrame.kStatus4, SparkMaxUtil.disableFramePeriod),
        () ->
            turnMotor.setPeriodicFramePeriod(
                PeriodicFrame.kStatus5, SparkMaxUtil.disableFramePeriod),
        () ->
            turnMotor.setPeriodicFramePeriod(
                PeriodicFrame.kStatus6, SparkMaxUtil.disableFramePeriod),
        () ->
            turnMotor.setPeriodicFramePeriod(
                PeriodicFrame.kStatus7, SparkMaxUtil.disableFramePeriod));
  }

  private void configureAngleEncoder() {
    CANcoderConfiguration configuration = new CANcoderConfiguration();
    MagnetSensorConfigs magnetSensorConfigs =
        new MagnetSensorConfigs()
            .withAbsoluteSensorRange(AbsoluteSensorRangeValue.Signed_PlusMinusHalf)
            .withSensorDirection(
                (!SwerveConstants.canCoderInverted)
                    ? SensorDirectionValue.CounterClockwise_Positive
                    : SensorDirectionValue.Clockwise_Positive);

    configuration.MagnetSensor = magnetSensorConfigs;
    configuration.FutureProofConfigs = true;

    canCoder.getConfigurator().apply(configuration, 0.100);
    canCoder.clearStickyFaults();

    absoluteAngleSignal.setUpdateFrequency(50);
    canCoder.getFaultField().setUpdateFrequency(4);
    canCoder.optimizeBusUtilization(0.100);
  }

  public void resetToAbsolute() {
    if (!waitForCANCoder()) {
      noAbsoluteValue.set(true);
      return;
    } else {
      noAbsoluteValue.set(false);
    }

    Rotation2d position = getAbsoluteAngle().minus(angleOffset);

    boolean failed = true;

    turnMotor.setCANTimeout(100);

    for (int i = 0; i < 5; i++) {
      if (turnEncoder.setPosition(position.getRadians()) == REVLibError.kOk) {
        failed = false;
      }
      if (turnEncoder.getPosition() == position.getRadians()) {
        break;
      }
      Timer.delay(0.010);
    }

    if (failed) {
      DataLogManager.log("Failed to set absolute angle of " + moduleName + " module!");
      DriverStation.reportError(
          "Failed to set absolute angle of " + moduleName + " module!", false);
      motorPositionNotSet.set(true);
    } else {
      motorPositionNotSet.set(false);
    }
    turnMotor.setCANTimeout(0);
  }

  private boolean waitForCANCoder() {
    for (int i = 0; i < 5; i++) {
      absoluteAngleSignal.waitForUpdate(0.1);

      if (absoluteAngleSignal.getStatus().isOK()) {
        return true;
      }
    }

    DataLogManager.log(
        "Failed to receive absolute position from CANCoder on " + moduleName + " Module");
    DriverStation.reportError(
        "Failed to receive absolute position from CANCoder on " + moduleName + " Module", false);

    return false;
  }

  public void setState(SwerveModuleState state, boolean isOpenLoop, boolean allowTurnInPlace) {
    SwerveModuleState optimizedState = SwerveModuleState.optimize(state, getAngle());

    if (optimizedState.speedMetersPerSecond == 0.0 && !allowTurnInPlace) {
      optimizedState.angle = desiredState.angle;

      optimizedState = SwerveModuleState.optimize(optimizedState, getAngle());
    }

    if (SwerveConstants.useCosineCompensation) {
      double cosineScalar = optimizedState.angle.minus(getAngle()).getCos();
      if (cosineScalar < 0.0) {
        cosineScalar = 1.0;
      }
      optimizedState.speedMetersPerSecond *= cosineScalar;
    }

    previousState = desiredState;
    desiredState = optimizedState;
    this.isOpenLoop = isOpenLoop;
    this.allowTurnInPlace = allowTurnInPlace;
  }

  public void setCharacterizationVolts(double volts) {
    characterizationVolts = volts;
    characterizing = true;
  }

  public void stopCharacterizing() {
    characterizationVolts = 0.0;
    characterizing = false;
  }

  public SwerveModulePosition getModulePosition() {
    return new SwerveModulePosition(getPosition(), getAngle());
  }

  public SwerveModuleState getModuleState() {
    return new SwerveModuleState(getVelocity(), getAngle());
  }

  public SwerveModuleState getDesiredState() {
    return desiredState;
  }

  public double getPositionRadians() {
    return getPosition() / SwerveConstants.wheelRadius;
  }

  public double getPosition() {
    if (RobotBase.isReal()) {
      return driveEncoder.getPosition();
    } else {
      return simPosition;
    }
  }

  public double getVelocity() {
    if (RobotBase.isReal()) {
      return driveEncoder.getVelocity();
    } else {
      return simVelocity;
    }
  }

  public Rotation2d getAngle() {
    if (RobotBase.isReal()) {
      return Rotation2d.fromRadians(turnEncoder.getPosition());
    } else {
      return Rotation2d.fromRadians(simAngle);
    }
  }

  public double getVoltage() {
    return driveMotor.getAppliedOutput() * driveMotor.getBusVoltage();
  }

  public Rotation2d getAbsoluteAngle() {
    return Rotation2d.fromRotations(absoluteAngleSignal.getValue());
  }

  public boolean driveMotorValid() {
    return driveMotor.getLastError() == REVLibError.kOk;
  }

  public boolean turnMotorValid() {
    return turnMotor.getLastError() == REVLibError.kOk;
  }

  public boolean motorsValid() {
    return driveMotorValid() && turnMotorValid();
  }

  private void setSpeed(double speedMetersPerSecond) {
    if (RobotBase.isReal()) {
      if (isOpenLoop) {
        driveMotor.set(speedMetersPerSecond / SwerveConstants.maxModuleSpeed);
      } else {
        double feedForward =
            driveFeedforward.calculate(
                speedMetersPerSecond,
                (speedMetersPerSecond - previousState.speedMetersPerSecond) / 0.020);

        drivePID.setReference(
            speedMetersPerSecond, ControlType.kVelocity, 0, feedForward, ArbFFUnits.kVoltage);
      }
    } else {
      if (isOpenLoop) {
        driveMotor.setVoltage(
            (speedMetersPerSecond / SwerveConstants.maxModuleSpeed)
                * RobotController.getBatteryVoltage());
      } else {
        double feedForward =
            driveFeedforward.calculate(
                speedMetersPerSecond,
                (speedMetersPerSecond - previousState.speedMetersPerSecond) / 0.020);
        feedForward = MathUtil.clamp(feedForward, -12.0, 12.0);
        double pidOutput = (getVelocity() - speedMetersPerSecond) * SwerveConstants.driveP;
        pidOutput = MathUtil.clamp(pidOutput, -1.0, 1.0);

        driveMotor.setVoltage(feedForward + pidOutput * RobotController.getBatteryVoltage());
      }
    }
  }

  private void setVoltage(double voltage) {
    driveMotor.setVoltage(voltage);
  }

  private void setAngle(Rotation2d angle) {
    if (RobotBase.isReal()) {
      turnPID.setReference(angle.getRadians(), ControlType.kPosition);
    } else {
      Rotation2d angleError = angle.minus(getAngle());

      double pidOutput = angleError.getRadians() * SwerveConstants.turnP;
      pidOutput = MathUtil.clamp(pidOutput, -1.0, 1.0);

      turnMotor.setVoltage(pidOutput * RobotController.getBatteryVoltage());
    }
  }

  public void periodic() {
    absoluteAngleSignal.refresh(false);

    if (!characterizing) {
      setSpeed(desiredState.speedMetersPerSecond);
      setAngle(desiredState.angle);
    } else {
      setVoltage(characterizationVolts);
      setAngle(Rotation2d.fromDegrees(0.0));
    }

    updateTelemetry();
  }

  public void simulationPeriodic() {
    if (DriverStation.isEnabled()) {
      driveSim.setInput(driveMotor.getAppliedOutput());
      turnSim.setInput(turnMotor.getAppliedOutput());
    } else {
      driveSim.setInput(0.0);
      turnSim.setInput(0.0);
    }

    driveSim.update(0.020);
    turnSim.update(0.020);

    simVelocity =
        driveSim.getAngularVelocityRadPerSec() * SwerveConstants.wheelCircumference / (2 * Math.PI);
    simPosition =
        driveSim.getAngularPositionRad() * SwerveConstants.wheelCircumference / (2 * Math.PI);

    simAngle = turnSim.getAngularPositionRad();
  }

  private void updateTelemetry() {
    String telemetryKey = "Swerve/" + moduleName + "/";

    SmartDashboard.putNumber(telemetryKey + "Position", getPosition());

    SmartDashboard.putNumber(telemetryKey + "Velocity", getVelocity());
    SmartDashboard.putNumber(telemetryKey + "Angle", getAngle().getDegrees());
    SmartDashboard.putNumber(telemetryKey + "Absolute Angle", getAbsoluteAngle().getDegrees());
    SmartDashboard.putNumber(telemetryKey + "Desired Velocity", desiredState.speedMetersPerSecond);
    SmartDashboard.putNumber(telemetryKey + "Desired Angle", desiredState.angle.getDegrees());
    SmartDashboard.putNumber(
        telemetryKey + "Velocity Error", desiredState.speedMetersPerSecond - getVelocity());
    SmartDashboard.putNumber(
        telemetryKey + "Angle Error", desiredState.angle.minus(getAngle()).getDegrees());

    SmartDashboard.putNumber(telemetryKey + "Drive Temperature", driveMotor.getMotorTemperature());
    SmartDashboard.putNumber(telemetryKey + "Turn Temperature", turnMotor.getMotorTemperature());
    SmartDashboard.putNumber(telemetryKey + "Drive Applied Output", driveMotor.getAppliedOutput());
    SmartDashboard.putNumber(telemetryKey + "Turn Applied Output", turnMotor.getAppliedOutput());
    SmartDashboard.putNumber(telemetryKey + "Drive Output Current", driveMotor.getOutputCurrent());
    SmartDashboard.putNumber(telemetryKey + "Turn Output Current", turnMotor.getOutputCurrent());

    SmartDashboard.putBoolean(telemetryKey + "Open Loop", isOpenLoop);
    SmartDashboard.putBoolean(telemetryKey + "Allow Turn in Place", allowTurnInPlace);
    SmartDashboard.putBoolean(telemetryKey + "Characterizing", characterizing);
    SmartDashboard.putNumber(telemetryKey + "Characterization Volts", characterizationVolts);
  }

  public Command getPrematchCommand(
      Consumer<String> onInfoAlert,
      Consumer<String> onWarningAlert,
      Consumer<String> onErrorAlert) {
    return Commands.sequence(
        // Check for errors in drive motor
        Commands.runOnce(
            () -> {
              REVLibError error = driveMotor.getLastError();

              if (error != REVLibError.kOk) {
                onErrorAlert.accept(moduleName + " Drive Motor error: " + error.name());
              } else {
                onInfoAlert.accept(moduleName + " Drive Motor contains no errors");
              }
            }),
        // Check for errors in turn motor
        Commands.runOnce(
            () -> {
              REVLibError error = turnMotor.getLastError();

              if (error != REVLibError.kOk) {
                onErrorAlert.accept(moduleName + " Turn Motor error: " + error.name());
              } else {
                onInfoAlert.accept(moduleName + " Turn Motor contains no errors");
              }
            }),
        // Check for errors in CANCoder
        Commands.runOnce(
            () -> {
              boolean errorDetected = false;

              if (canCoder.getFault_BadMagnet().getValue()) {
                onErrorAlert.accept(moduleName + " CANCoder bad magnet");
                errorDetected = true;
              }
              if (canCoder.getFault_BootDuringEnable().getValue()) {
                onErrorAlert.accept(moduleName + " CANCoder booted while enabled");
                errorDetected = true;
              }
              if (canCoder.getFault_Hardware().getValue()) {
                onErrorAlert.accept(moduleName + " CANCoder hardware fault detected");
                errorDetected = true;
              }
              if (canCoder.getFault_Undervoltage().getValue()) {
                onErrorAlert.accept(moduleName + " CANCoder under voltage");
                errorDetected = true;
              }
              if (canCoder.getFault_UnlicensedFeatureInUse().getValue()) {
                onErrorAlert.accept(moduleName + " CANCoder unlicensed feature in use");
                errorDetected = true;
              }

              if (!errorDetected) {
                onInfoAlert.accept(moduleName + " CANCoder has no errors");
              }
            }),
        // Check if drive motor is in brake mode
        Commands.runOnce(
            () -> {
              if (driveMotor.getIdleMode() != IdleMode.kBrake) {
                onWarningAlert.accept(
                    moduleName
                        + " Drive Motor (Motor ID: "
                        + driveMotor.getDeviceId()
                        + ") is not in brake mode");
              } else {
                onInfoAlert.accept(
                    moduleName
                        + " Drive Motor (Motor ID: "
                        + driveMotor.getDeviceId()
                        + ") is in brake mode");
              }
            }),
        // Check if turn motor is in coast mode
        Commands.runOnce(
            () -> {
              if (turnMotor.getIdleMode() != IdleMode.kCoast) {
                onWarningAlert.accept(
                    moduleName
                        + " Turn Motor (Motor ID: "
                        + turnMotor.getDeviceId()
                        + ") is not in coast mode");
              } else {
                onInfoAlert.accept(
                    moduleName
                        + " Turn Motor (Motor ID: "
                        + turnMotor.getDeviceId()
                        + ") is in coast mode");
              }
            }));
  }
}
