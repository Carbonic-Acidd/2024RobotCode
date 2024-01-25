// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.commands.leds;

import edu.wpi.first.wpilibj.AddressableLEDBuffer;
import edu.wpi.first.wpilibj.util.Color;
import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.Constants.LEDConstants;
import frc.robot.subsystems.Intake;
import frc.robot.subsystems.LEDs;

public class IntakeLight extends Command {
  /** Creates a new IntakeLight. */
    private final Color color;
    private final LEDs leds;
    private final Intake intake;

     public IntakeLight(Intake intake,Color color, LEDs leds) {
      this.color = color;
      this.leds = leds;
      this.intake = intake;

      addRequirements(leds);
      addRequirements(intake);
    }

    // Use addRequirements() here to declare subsystem dependencies.


  // Called when the command is initially scheduled.
  @Override
  public void initialize() {}

  // Called every time the scheduler runs while the command is scheduled.
  @Override
  public void execute() {

    AddressableLEDBuffer buffer = leds.getBuffer();

    if(intake.isIRBeamBreakBroken()) {
      for (int i = 0; i < buffer.getLength(); i++) {
        buffer.setLED(i, color);
      }
    } else {
       for (int i = 0; i < buffer.getLength(); i++) {
        buffer.setLED(i, LEDConstants.transparent);
    }

  }
  }
  // Called once the command ends or is interrupted.
  @Override
  public void end(boolean interrupted) {}

  // Returns true when the command should end.
  @Override
  public boolean isFinished() {
    return false;
  }
}
