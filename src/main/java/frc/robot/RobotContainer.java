// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.auto.NamedCommands;
import com.pathplanner.lib.path.PathConstraints;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.PowerDistribution;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj.util.Color;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.ProxyCommand;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import edu.wpi.first.wpilibj2.command.button.Trigger;
import frc.lib.controllers.VirtualJoystick;
import frc.lib.controllers.VirtualXboxController;
import frc.robot.Constants.ArmConstants;
import frc.robot.Constants.AutoConstants;
import frc.robot.Constants.FieldConstants;
import frc.robot.Constants.OperatorConstants;
import frc.robot.Constants.ShooterConstants;
import frc.robot.Constants.SwerveConstants;
import frc.robot.Constants.VisionConstants;
import frc.robot.commands.ShootWhileMoving;
import frc.robot.commands.StartupConnectionCheck;
import frc.robot.commands.TeleopSwerve;
import frc.robot.commands.arm.ArmHold;
import frc.robot.commands.leds.LoadingAnimation;
import frc.robot.commands.leds.RSLSync;
import frc.robot.commands.leds.SolidColor;
import frc.robot.subsystems.Arm;
import frc.robot.subsystems.Climber;
import frc.robot.subsystems.Intake;
import frc.robot.subsystems.LEDs;
import frc.robot.subsystems.Shooter;
import frc.robot.subsystems.Swerve;
import frc.robot.util.Alert;
import frc.robot.util.Alert.AlertType;
import frc.robot.util.AllianceUtil;
import frc.robot.util.LogUtil;
import frc.robot.util.PersistentSendableChooser;
import java.util.ArrayList;
import java.util.List;
import monologue.Logged;

/**
 * This class is where the bulk of the robot should be declared. Since Command-based is a
 * "declarative" paradigm, very little robot logic should actually be handled in the {@link Robot}
 * periodic methods (other than the scheduler calls). Instead, the structure of the robot (including
 * subsystems, commands, and trigger mappings) should be declared here.
 */
public class RobotContainer implements Logged {
  // The robot's subsystems and commands are defined here...
  private Swerve swerve = new Swerve();
  private Arm arm = new Arm();
  private Intake intake = new Intake();
  private Shooter shooter = new Shooter();
  private Climber climber = new Climber();
  private LEDs leds = new LEDs();

  private final PathConstraints pathfindingConstraints =
      new PathConstraints(
          AutoConstants.pathfindingMaxVelocity,
          AutoConstants.pathfindingMaxAcceleration,
          AutoConstants.pathfindingMaxAngularVelocity,
          AutoConstants.pathfindingMaxAngularAcceleration);

  // Replace with CommandPS4Controller or CommandJoystick if needed
  private final VirtualXboxController driverController =
      new VirtualXboxController(OperatorConstants.driverControllerPort);
  private final VirtualJoystick operatorStick =
      new VirtualJoystick(OperatorConstants.operatorControllerPort);

  private PowerDistribution powerDistribution = new PowerDistribution();

  private PersistentSendableChooser<String> batteryChooser =
      new PersistentSendableChooser<>("Battery Number");
  private SendableChooser<Command> autoChooser;

  private List<Alert> alerts = new ArrayList<Alert>();
  private Alert armPrematchAlert = new Alert("", AlertType.INFO);
  private Alert intakePrematchAlert = new Alert("", AlertType.INFO);
  private Alert shooterPrematchAlert = new Alert("", AlertType.INFO);
  private Alert swervePrematchAlert = new Alert("", AlertType.INFO);
  private Alert generalPrematchAlert = new Alert("", AlertType.INFO);

  /** The container for the robot. Contains subsystems, OI devices, and commands. */
  public RobotContainer() {
    // Configure the trigger bindings
    configureBindings();
    configureBatteryChooser();
    configurePrematchChecklist();

    NamedCommands.registerCommand("Start Intake", intake.intakeUntilBeamBreak());
    NamedCommands.registerCommand(
        "Intake Until Beam Break", intake.intakeUntilBeamBreak().withTimeout(0.25));
    NamedCommands.registerCommand("Stop Intake", intake.runOnce(intake::stopIntakeMotor));

    NamedCommands.registerCommand(
        "Arm to Pickup", arm.moveToPosition(ArmConstants.pickupAngle).withTimeout(3.0).asProxy());
    NamedCommands.registerCommand(
        "Arm to Subwoofer",
        arm.moveToPosition(ArmConstants.subwooferAngle).withTimeout(1.25).asProxy());
    NamedCommands.registerCommand(
        "Arm to Source Podium",
        arm.moveToPosition(ArmConstants.autoSourcePodiumAngle).withTimeout(3.0).asProxy());
    NamedCommands.registerCommand(
        "Arm to Amp Podium",
        arm.autoMoveToPosition(ArmConstants.autoAmpPodiumAngle).withTimeout(3.0).asProxy());
    NamedCommands.registerCommand(
        "Arm to Close Shoot",
        arm.autoMoveToPosition(ArmConstants.autoCloseShootAngle).withTimeout(3.0).asProxy());
    NamedCommands.registerCommand(
        "Arm to Wing Shoot",
        arm.autoMoveToPosition(ArmConstants.autoWingShotAngle).withTimeout(3.0).asProxy());
    NamedCommands.registerCommand(
        "Arm to Center Wing Shoot",
        arm.autoMoveToPosition(ArmConstants.autoCenterWingShotAngle).withTimeout(3.0).asProxy());
    NamedCommands.registerCommand(
        "Arm to Amp Wing",
        arm.autoMoveToPosition(ArmConstants.autoAmpWingAngle).withTimeout(3.0).asProxy());
    NamedCommands.registerCommand(
        "Arm to Behind W1",
        arm.autoMoveToPosition(ArmConstants.behindWing1Angle).withTimeout(3.0).asProxy());
    NamedCommands.registerCommand(
        "Arm to Behind W2",
        arm.autoMoveToPosition(ArmConstants.behindWing2Angle).withTimeout(3.0).asProxy());

    NamedCommands.registerCommand(
        "Warm Up Shooter",
        shooter.run(() -> shooter.setMotorSpeed(ShooterConstants.shooterVelocity)));
    NamedCommands.registerCommand(
        "Shoot",
        intake
            .autoFeedToShooter()
            .withTimeout(1.0)
            .finallyDo(
                () -> {
                  intake.stopIntakeMotor();
                  shooter.stopMotor();
                }));

    configureAutoChooser();

    SmartDashboard.putData("Command Scheduler", CommandScheduler.getInstance());
    SmartDashboard.putData("Power Distribution Panel", powerDistribution);

    LogUtil.recordMetadata("Battery Number", batteryChooser.getSelectedName());
    LogUtil.recordMetadata("Battery Nickname", batteryChooser.getSelected());

    leds.setDefaultCommand(new RSLSync(leds));

    arm.setDefaultCommand(new ArmHold(arm));

    swerve.setDefaultCommand(
        new TeleopSwerve(
            driverController::getLeftY,
            driverController::getLeftX,
            driverController::getRightX,
            driverController::getRightY,
            driverController::getLeftBumper,
            SwerveConstants.maxTranslationalSpeed,
            SwerveConstants.maxAngularSpeed,
            swerve));

    new StartupConnectionCheck(
            new LoadingAnimation(Color.kBlue, leds),
            new SolidColor(Color.kGreen, leds).withTimeout(5.0),
            new SolidColor(Color.kRed, leds).withTimeout(5.0))
        .schedule();
  }

  /**
   * Use this method to define your trigger->command mappings. Triggers can be created via the
   * {@link Trigger#Trigger(java.util.function.BooleanSupplier)} constructor with an arbitrary
   * predicate, or via the named factories in {@link
   * edu.wpi.first.wpilibj2.command.button.CommandGenericHID}'s subclasses for {@link
   * CommandXboxController Xbox}/{@link edu.wpi.first.wpilibj2.command.button.CommandPS4Controller
   * PS4} controllers or {@link edu.wpi.first.wpilibj2.command.button.CommandJoystick Flight
   * joysticks}.
   */
  private void configureBindings() {
    configureDriveBindings();
    configureIntakeBindings();
    configureArmBindings();
    configureShooterBindings();
    configureClimbingBindings();

    operatorStick
        .button(OperatorConstants.ledWarningButton)
        .whileTrue(new SolidColor(Color.kYellow, leds));
  }

  private void configureDriveBindings() {
    driverController
        .back()
        .and(driverController.start())
        .onTrue(Commands.runOnce(swerve::zeroYaw).ignoringDisable(true));

    driverController
        .leftStick()
        .and(driverController.rightStick())
        .onTrue(
            Commands.sequence(
                    Commands.runOnce(
                        () -> {
                          arm.resetToAbsolute();
                          swerve.reconfigureTurnMotors();
                        }),
                    Commands.waitSeconds(1.0),
                    Commands.runOnce(
                        () -> {
                          swerve.resetModulesToAbsolute();
                        }))
                .ignoringDisable(true));

    driverController.x().whileTrue(swerve.run(swerve::lockModules));

    driverController
        .leftTrigger()
        .and(driverController.rightTrigger())
        .whileTrue(
            new ProxyCommand(
                () -> {
                  if (AllianceUtil.isRedAlliance()) {
                    return AutoBuilder.pathfindToPose(
                        new Pose2d(
                            FieldConstants.driverStationRedAlliance.getX(),
                            FieldConstants.driverStationRedAlliance.getY(),
                            FieldConstants.driverStationRedAlliance.getRotation()),
                        new PathConstraints(
                            SwerveConstants.maxTranslationalSpeed,
                            SwerveConstants.maxTranslationalAcceleration,
                            Units.degreesToRadians(180.0),
                            180.0));
                  } else {
                    return AutoBuilder.pathfindToPose(
                        new Pose2d(
                            FieldConstants.driverStationBlueAlliance.getX(),
                            FieldConstants.driverStationBlueAlliance.getY(),
                            FieldConstants.driverStationBlueAlliance.getRotation()),
                        new PathConstraints(
                            SwerveConstants.maxTranslationalSpeed,
                            SwerveConstants.maxTranslationalAcceleration,
                            Units.degreesToRadians(180.0),
                            180.0));
                  }
                }));

    driverController
        .leftBumper()
        .and(driverController.rightBumper())
        .whileTrue(
            new ProxyCommand(
                () -> {
                  if (AllianceUtil.isRedAlliance()) {
                    return AutoBuilder.pathfindToPose(
                        new Pose2d(
                            FieldConstants.speakerRedAlliance.getX(),
                            FieldConstants.speakerRedAlliance.getY(),
                            FieldConstants.speakerRedAlliance.getRotation()),
                        new PathConstraints(
                            SwerveConstants.maxTranslationalSpeed,
                            SwerveConstants.maxTranslationalAcceleration,
                            Units.degreesToRadians(180.0),
                            180.0));
                  } else {
                    return AutoBuilder.pathfindToPose(
                        new Pose2d(
                            FieldConstants.speakerBlueAlliance.getX(),
                            FieldConstants.speakerBlueAlliance.getY(),
                            FieldConstants.speakerBlueAlliance.getRotation()),
                        new PathConstraints(
                            SwerveConstants.maxTranslationalSpeed,
                            SwerveConstants.maxTranslationalAcceleration,
                            Units.degreesToRadians(180.0),
                            180.0));
                  }
                }));

    driverController
        .leftTrigger()
        .whileTrue(
            new TeleopSwerve(
                driverController::getLeftY,
                driverController::getLeftX,
                driverController::getRightX,
                driverController::getRightY,
                () -> driverController.getLeftBumper(),
                SwerveConstants.slowMotionMaxTranslationalSpeed,
                SwerveConstants.maxAngularSpeed,
                swerve));

    driverController
        .rightTrigger()
        .whileTrue(
            new TeleopSwerve(
                driverController::getLeftY,
                driverController::getLeftX,
                driverController::getRightX,
                driverController::getRightY,
                () -> driverController.getLeftBumper(),
                SwerveConstants.turboMaxTranslationalSpeed,
                SwerveConstants.maxAngularSpeed,
                swerve));

    driverController
        .x()
        .whileTrue(
            new ProxyCommand(
                () ->
                    AutoBuilder.pathfindToPose(swerve.getLeftChainPose(), pathfindingConstraints)));

    driverController
        .y()
        .whileTrue(
            new ProxyCommand(
                () ->
                    AutoBuilder.pathfindToPose(
                        swerve.getCenterChainPose(), pathfindingConstraints)));

    driverController
        .b()
        .whileTrue(
            new ProxyCommand(
                () ->
                    AutoBuilder.pathfindToPose(
                        swerve.getRightChainPose(), pathfindingConstraints)));
  }

  private void configureIntakeBindings() {
    operatorStick
        .button(OperatorConstants.intakeNoteButton)
        .whileTrue(intake.intakeUntilBeamBreak())
        .onFalse(intake.runOnce(intake::stopIntakeMotor));

    operatorStick
        .button(OperatorConstants.manualOuttakeButton)
        .whileTrue(intake.run(intake::outtakeNoteInIntake))
        .onFalse(intake.runOnce(intake::stopIntakeMotor));

    operatorStick
        .button(OperatorConstants.manualFeedButton)
        .whileTrue(intake.autoFeedToShooter())
        .onFalse(intake.runOnce(intake::stopIntakeMotor).ignoringDisable(true));
  }

  private void configureClimbingBindings() {
    Trigger reverseDirection = operatorStick.povUp();

    operatorStick
        .button(OperatorConstants.leftClimberButton)
        .and(reverseDirection.negate())
        .whileTrue(climber.run(climber::climbLeft))
        .onFalse(climber.runOnce(climber::stopLeft));

    operatorStick
        .button(OperatorConstants.rightClimberButton)
        .and(reverseDirection.negate())
        .whileTrue(climber.run(climber::climbRight))
        .onFalse(climber.runOnce(climber::stopRight));

    operatorStick
        .button(OperatorConstants.climberButton)
        .whileTrue(
            climber.run(climber::climbBoth).onlyIf(() -> DriverStation.getMatchTime() <= 20.0))
        .onFalse(climber.runOnce(climber::stopClimberMotors));

    operatorStick
        .button(OperatorConstants.rightClimberButton)
        .and(reverseDirection)
        .whileTrue(climber.run(climber::rightReverse))
        .onFalse(climber.runOnce(climber::stopRight));

    operatorStick
        .button(OperatorConstants.leftClimberButton)
        .and(reverseDirection)
        .whileTrue(climber.run(climber::leftReverse))
        .onFalse(climber.runOnce(climber::stopLeft));
  }

  private void configureArmBindings() {
    Trigger armManualUp = operatorStick.button(OperatorConstants.armManualUp);
    Trigger armManualDown = operatorStick.button(OperatorConstants.armManualDown);
    Trigger armSlowAdjustment = operatorStick.button(OperatorConstants.armPreciseManualAdjustment);

    operatorStick
        .button(OperatorConstants.armToPickup)
        .whileTrue(arm.moveToPosition(ArmConstants.pickupAngle));

    operatorStick
        .button(OperatorConstants.armToAmp)
        .whileTrue(arm.moveToPosition(ArmConstants.ampAngle));

    operatorStick
        .button(OperatorConstants.armToSubwoofer)
        .whileTrue(arm.moveToPosition(ArmConstants.subwooferAngle));

    operatorStick
        .button(OperatorConstants.armToPodium)
        .whileTrue(arm.moveToPosition(ArmConstants.podiumAngle));

    armManualUp
        .and(armSlowAdjustment.negate())
        .whileTrue(arm.run(() -> arm.setSpeed(ArmConstants.manualSpeed)))
        .onFalse(arm.runOnce(() -> arm.setSpeed(0.0)).ignoringDisable(true));

    armManualDown
        .and(armSlowAdjustment.negate())
        .whileTrue(arm.run(() -> arm.setSpeed(-ArmConstants.manualSpeed)))
        .onFalse(arm.runOnce(() -> arm.setSpeed(0.0)).ignoringDisable(true));

    // operatorStick
    //     .button(OperatorConstants.armAutoShoot)
    //     .whileTrue(new AutoShoot(arm, intake, shooter, swerve));

    operatorStick
        .button(OperatorConstants.armAutoShoot)
        .whileTrue(
            new ShootWhileMoving(
                driverController::getLeftY,
                driverController::getLeftX,
                SwerveConstants.slowMotionMaxTranslationalSpeed,
                arm,
                intake,
                shooter,
                swerve));

    armManualUp
        .and(armSlowAdjustment)
        .whileTrue(arm.run(() -> arm.setSpeed(ArmConstants.preciseManualSpeed)))
        .onFalse(arm.runOnce(() -> arm.setSpeed(0.0)).ignoringDisable(true));

    armManualDown
        .and(armSlowAdjustment)
        .whileTrue(arm.run(() -> arm.setSpeed(-ArmConstants.preciseManualSpeed)))
        .onFalse(arm.runOnce(() -> arm.setSpeed(0.0)).ignoringDisable(true));
  }

  private void configureShooterBindings() {
    Trigger ampShooter = operatorStick.button(OperatorConstants.armPreciseManualAdjustment);

    // operatorStick
    //     .button(OperatorConstants.shootButton)
    //     .and(ampShooter.negate())
    //     .whileTrue(
    //         Commands.run(() -> shooter.setMotorSpeed(ShooterConstants.shooterVelocity), shooter))
    //     .toggleOnFalse(shooter.runOnce(() -> shooter.stopMotor()));

    // operatorStick
    //     .button(OperatorConstants.shootButton)
    //     .and(ampShooter)
    //     .whileTrue(Commands.run(() -> shooter.setMotorSpeed(ShooterConstants.ampVelocity),
    // shooter))
    //     .toggleOnFalse(shooter.runOnce(() -> shooter.stopMotor()));

    operatorStick
        .button(OperatorConstants.manualShootButton)
        .whileTrue(
            shooter
                .run(
                    () -> {
                      if (arm.getPosition().getRadians() > ArmConstants.ampSpeedAngle) {
                        shooter.setMotorSpeed(ShooterConstants.ampVelocity);
                      } else {
                        shooter.setMotorSpeed(ShooterConstants.shooterVelocity);
                      }
                    })
                .ignoringDisable(true)
                .finallyDo(shooter::stopMotor))
        .onFalse(shooter.runOnce(shooter::stopMotor).ignoringDisable(true));

    // operatorStick
    //     .button(OperatorConstants.manualShootButton)
    //     .whileTrue(
    //         shooter
    //             .run(
    //                 () -> shooter.setMotorSpeedDifferential(ShooterConstants.shooterVelocity,
    // ShooterConstants.shooterVelocity))
    //             .finallyDo(shooter::stopMotor))
    //     .onFalse(shooter.runOnce(shooter::stopMotor).ignoringDisable(true));
  }

  private void configureAutoChooser() {
    autoChooser = AutoBuilder.buildAutoChooser();

    autoChooser.addOption("[SysID] Swerve Quasistatic Forward", swerve.quasistaticForward());
    autoChooser.addOption("[SysID] Swerve Quasistatic Backward", swerve.quasistaticBackward());
    autoChooser.addOption("[SysID] Swerve Dynamic Forward", swerve.dynamicForward());
    autoChooser.addOption("[SysID] Swerve Dynamic Backward", swerve.dynamicBackward());

    autoChooser.addOption("[SysID] Arm Quasistatic Forward", arm.quasistaticForward());
    autoChooser.addOption("[SysID] Arm Quasistatic Backward", arm.quasistaticBackward());
    autoChooser.addOption("[SysID] Arm Dynamic Forward", arm.dynamicForward());
    autoChooser.addOption("[SysID] Arm Dynamic Backward", arm.dynamicBackward());

    autoChooser.addOption("[SysID] Shooter Quasistatic Forward", shooter.quasistaticForward());
    autoChooser.addOption("[SysID] Shooter Quasistatic Backward", shooter.quasistaticBackward());
    autoChooser.addOption("[SysID] Shooter Dynamic Forward", shooter.dynamicForward());
    autoChooser.addOption("[SysID] Shooter Dynamic Backward", shooter.dynamicBackward());

    SmartDashboard.putData("Auto Chooser", autoChooser);
  }

  private void configureBatteryChooser() {
    batteryChooser.addOption("2015 #1", "Jerry");
    batteryChooser.addOption("2015 #2", "Bob");
    batteryChooser.addOption("2015 #3", "Omar");
    batteryChooser.addOption("2016 #2", "Ella");
    batteryChooser.addOption("2017 #1", "2017 #1");
    batteryChooser.addOption("2018 #1", "Larry");
    batteryChooser.addOption("2019.5 #2", "Allan");
    batteryChooser.addOption("2019.5 #3", "Daniel");
    batteryChooser.addOption("2020 #1", "Karen");
    batteryChooser.addOption("2020 #2", "Gary");
    batteryChooser.addOption("2020 #3", "Harold");
    batteryChooser.addOption("2021 #1", "Fred");
    batteryChooser.addOption("2022 #1", "Charles");
    batteryChooser.addOption("2024 #1", "Ian");
    batteryChooser.addOption("2024 #2", "Nancy");

    SmartDashboard.putData("Battery Chooser", batteryChooser);
  }

  private void configurePrematchChecklist() {
    Command generalPreMatch =
        Commands.sequence(
                Commands.runOnce(
                    () -> {
                      clearPrematchAlerts();
                    }),
                Commands.runOnce(
                    () -> {
                      if (!driverController.getHID().isConnected()) {
                        addError("Driver controller is not connected");
                      } else {
                        addInfo("Driver controller is connected");
                      }
                      if (!operatorStick.getHID().isConnected()) {
                        addError("Operator joystick is not connected");
                      } else {
                        addInfo("Operator joystick is connected");
                      }
                    }),
                Commands.runOnce(
                    () -> {
                      if (!DriverStation.getJoystickIsXbox(
                          OperatorConstants.driverControllerPort)) {
                        addError("Controller port 0 is not an Xbox controller");
                      } else {
                        addInfo("Controller port 0 is the correct joystick type (Xbox Controller)");
                      }

                      if (DriverStation.getJoystickIsXbox(
                          OperatorConstants.operatorControllerPort)) {
                        addError("Controller port 1 is not a generic joystick");
                      } else {
                        addInfo("Controller port 1 is the correct joystick type");
                      }
                    }),
                Commands.runOnce(
                    () -> {
                      NetworkTable rootTable = NetworkTableInstance.getDefault().getTable("");

                      if (!rootTable.containsSubTable(VisionConstants.limelightName)) {
                        addError("Limelight is not connected");
                      } else {
                        addInfo("Limelight is connected");
                      }

                      if (!swerve.arducamConnected()) {
                        addError("Arducam is not connected");
                      } else {
                        addInfo("Arudcam is connected");
                      }
                    }))
            .until(this::errorsPresent)
            .andThen(
                () -> {
                  generalPrematchAlert.removeFromGroup();
                  alerts.remove(generalPrematchAlert);
                  if (errorsPresent()) {
                    generalPrematchAlert = new Alert("General Pre-Match Failed!", AlertType.ERROR);
                  } else {
                    generalPrematchAlert =
                        new Alert("General Pre-Match Successful!", AlertType.INFO);
                  }
                  addAlert(generalPrematchAlert);
                })
            .unless(DriverStation::isFMSAttached)
            .withName("General Pre-Match");

    Command swervePreMatch =
        swerve
            .buildPrematch(driverController, operatorStick)
            .finallyDo(
                (interrupted) -> {
                  swervePrematchAlert.removeFromGroup();
                  alerts.remove(swervePrematchAlert);

                  if (swerve.containsErrors()) {
                    swervePrematchAlert = new Alert("Swerve Pre-Match Failed!", AlertType.ERROR);
                  } else {
                    swervePrematchAlert = new Alert("Swerve Pre-Match Successful!", AlertType.INFO);
                  }
                  addAlert(swervePrematchAlert);
                })
            .unless(DriverStation::isFMSAttached)
            .withName("Swerve Pre-Match");

    Command armPrematch =
        arm.buildPrematch(driverController, operatorStick)
            .finallyDo(
                (interrupted) -> {
                  armPrematchAlert.removeFromGroup();
                  alerts.remove(armPrematchAlert);

                  if (arm.containsErrors()) {
                    armPrematchAlert = new Alert("Arm Pre-Match Failed!", AlertType.ERROR);
                  } else {
                    armPrematchAlert = new Alert("Arm Pre-Match Successful!", AlertType.INFO);
                  }
                  addAlert(armPrematchAlert);
                });

    Command intakePrematch =
        intake
            .buildPrematch(driverController, operatorStick)
            .finallyDo(
                (interrupted) -> {
                  intakePrematchAlert.removeFromGroup();
                  alerts.remove(intakePrematchAlert);

                  if (intake.containsErrors()) {
                    intakePrematchAlert = new Alert("Intake Pre-Match Failed!", AlertType.ERROR);
                  } else {
                    intakePrematchAlert = new Alert("Intake Pre-Match Successful!", AlertType.INFO);
                  }
                  addAlert(intakePrematchAlert);
                });

    Command shooterPrematch =
        shooter
            .buildPrematch(driverController, operatorStick)
            .finallyDo(
                (interrupted) -> {
                  shooterPrematchAlert.removeFromGroup();
                  alerts.remove(shooterPrematchAlert);

                  if (shooter.containsErrors()) {
                    shooterPrematchAlert = new Alert("Shooter Pre-Match Failed!", AlertType.ERROR);
                  } else {
                    shooterPrematchAlert =
                        new Alert("Shooter Pre-Match Successful!", AlertType.INFO);
                  }
                  addAlert(shooterPrematchAlert);
                });

    SmartDashboard.putData(
        "Full Pre-Match",
        Commands.sequence(
                Commands.runOnce(
                    () -> {
                      clearPrematchAlerts();
                    }),
                generalPreMatch.asProxy(),
                swervePreMatch.asProxy(),
                armPrematch.asProxy(),
                intakePrematch.asProxy(),
                shooterPrematch.asProxy(),
                Commands.runOnce(
                    () -> {
                      if (!errorsPresent()) {
                        addInfo(
                            "Pre-Match Successful! Good luck in the next match! Let's kick bot!");
                      } else {
                        addError("Pre-Match Failed!");
                      }
                    }))
            .unless(DriverStation::isFMSAttached)
            .withName("Full Pre-Match"));

    SmartDashboard.putData("General Pre-Match Check", generalPreMatch.asProxy());
    SmartDashboard.putData("Swerve/Swerve Pre-Match Check", swervePreMatch.asProxy());
    SmartDashboard.putData("Arm/Arm Pre-Match Check", armPrematch.asProxy());
    SmartDashboard.putData("Intake/Intake Pre-Match Check", intakePrematch.asProxy());
    SmartDashboard.putData("Shooter/Shooter Pre-Match Check", shooterPrematch.asProxy());
  }

  private void clearPrematchAlerts() {
    for (Alert alert : alerts) {
      alert.removeFromGroup();
    }

    alerts.clear();
  }

  private void addAlert(Alert alert) {
    alert.set(true);
    alerts.add(alert);
  }

  private void addInfo(String message) {
    addAlert(new Alert(message, AlertType.INFO));
  }

  private void addError(String message) {
    addAlert(new Alert(message, AlertType.ERROR));
  }

  private boolean errorsPresent() {
    for (Alert alert : alerts) {
      if (alert.getType() == AlertType.ERROR) {
        return true;
      }
    }

    return false;
  }

  public void updateSwerveOdometry() {
    swerve.updateOdometry();
  }

  /**
   * Use this to pass the autonomous command to the main {@link Robot} class.
   *
   * @return the command to run in autonomous
   */
  public Command getAutonomousCommand() {
    return autoChooser.getSelected();
  }
}
