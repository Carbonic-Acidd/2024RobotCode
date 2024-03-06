// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;

import com.revrobotics.CANSparkBase.IdleMode;
import com.revrobotics.CANSparkLowLevel.MotorType;
import com.revrobotics.CANSparkLowLevel.PeriodicFrame;
import com.revrobotics.CANSparkMax;
import com.revrobotics.REVLibError;
import com.revrobotics.RelativeEncoder;
import edu.wpi.first.wpilibj.DataLogManager;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import frc.lib.controllers.VirtualJoystick;
import frc.lib.controllers.VirtualXboxController;
import frc.lib.subsystem.VirtualSubsystem;
import frc.robot.Constants.ClimberConstants;
import frc.robot.Constants.OperatorConstants;
import frc.robot.util.FaultLogger;
import frc.robot.util.SparkMaxUtil;

public class Climber extends VirtualSubsystem {
  private CANSparkMax leftMotor =
      new CANSparkMax(ClimberConstants.leftMotorID, MotorType.kBrushless);
  private CANSparkMax rightMotor =
      new CANSparkMax(ClimberConstants.rightMotorID, MotorType.kBrushless);

  private RelativeEncoder climberEncoder = leftMotor.getEncoder();

  private final double prematchDelay = 2.5;

  public Climber() {
    DataLogManager.log("[Climber] Initializing");
    DataLogManager.log("[Climber] Configuring Left Motor");
    SparkMaxUtil.configure(
        leftMotor,
        () -> SparkMaxUtil.setInverted(leftMotor, true),
        () -> leftMotor.setIdleMode(IdleMode.kBrake),
        () -> leftMotor.setSmartCurrentLimit(ClimberConstants.climberCurrentLimit),
        () -> leftMotor.setPeriodicFramePeriod(PeriodicFrame.kStatus0, 20),
        () ->
            leftMotor.setPeriodicFramePeriod(
                PeriodicFrame.kStatus3, SparkMaxUtil.disableFramePeriod),
        () ->
            leftMotor.setPeriodicFramePeriod(
                PeriodicFrame.kStatus4, SparkMaxUtil.disableFramePeriod),
        () ->
            leftMotor.setPeriodicFramePeriod(
                PeriodicFrame.kStatus5, SparkMaxUtil.disableFramePeriod),
        () ->
            leftMotor.setPeriodicFramePeriod(
                PeriodicFrame.kStatus6, SparkMaxUtil.disableFramePeriod),
        () ->
            leftMotor.setPeriodicFramePeriod(
                PeriodicFrame.kStatus7, SparkMaxUtil.disableFramePeriod));

    DataLogManager.log("[Climber] Configuring Right Motor");
    SparkMaxUtil.configure(
        rightMotor,
        () -> SparkMaxUtil.setInverted(rightMotor, true),
        () -> rightMotor.setIdleMode(IdleMode.kBrake),
        () -> rightMotor.setSmartCurrentLimit(ClimberConstants.climberCurrentLimit),
        () -> rightMotor.setPeriodicFramePeriod(PeriodicFrame.kStatus0, 20),
        () ->
            rightMotor.setPeriodicFramePeriod(
                PeriodicFrame.kStatus3, SparkMaxUtil.disableFramePeriod),
        () ->
            rightMotor.setPeriodicFramePeriod(
                PeriodicFrame.kStatus4, SparkMaxUtil.disableFramePeriod),
        () ->
            rightMotor.setPeriodicFramePeriod(
                PeriodicFrame.kStatus5, SparkMaxUtil.disableFramePeriod),
        () ->
            rightMotor.setPeriodicFramePeriod(
                PeriodicFrame.kStatus6, SparkMaxUtil.disableFramePeriod),
        () ->
            rightMotor.setPeriodicFramePeriod(
                PeriodicFrame.kStatus7, SparkMaxUtil.disableFramePeriod));

    FaultLogger.register(leftMotor);
    FaultLogger.register(rightMotor);

    DataLogManager.log("[Climber] Initialization Complete");
  }

  public double getVelocity() {
    return climberEncoder.getVelocity();
  }

  public void climbBoth() {
    leftMotor.set(ClimberConstants.climberMotorSpeed);
    rightMotor.set(ClimberConstants.climberMotorSpeed);
  }

  public void climbLeft() {
    leftMotor.set(ClimberConstants.climberMotorSpeed);
  }

  public void climbRight() {
    rightMotor.set(ClimberConstants.climberMotorSpeed);
  }

  public void stopClimberMotors() {
    leftMotor.set(0.0);
    rightMotor.set(0.0);
  }

  public void stopLeft() {
    leftMotor.set(0.0);
  }

  public void stopRight() {
    rightMotor.set(0.0);
  }

  public void leftReverse() {
    leftMotor.set(-ClimberConstants.climberMotorSpeed);
  }

  public void rightReverse() {
    rightMotor.set(-ClimberConstants.climberMotorSpeed);
  }

  @Override
  public void periodic() {
    SmartDashboard.putNumber("Climber/Temperature", leftMotor.getMotorTemperature());
    SmartDashboard.putNumber("Climber/Current", leftMotor.getOutputCurrent());
    SmartDashboard.putNumber("Climber/Applied Output", leftMotor.getAppliedOutput());
  }

  @Override
  public Command getPrematchCheckCommand(
      VirtualXboxController controller, VirtualJoystick joystick) {
    return Commands.sequence(
        // Check for hardware motors
        Commands.runOnce(
            () -> {
              REVLibError error = leftMotor.getLastError();
              if (error != REVLibError.kOk) {
                addError("Climber motor error: " + error.name());
              } else {
                addInfo("Climber motor contains no errors");
              }
            }),
        // Checks climber motor
        Commands.runOnce(
            () -> {
              joystick.setButton(OperatorConstants.climberButton, true);
            }),
        Commands.waitSeconds(prematchDelay),
        Commands.runOnce(
            () -> {
              if (getVelocity() < 10) {
                addError("Climber motor isn't working");
              } else {
                addInfo("Climber motor is moving");
              }
              joystick.clearVirtualButtons();
            }));
  }
}
