package frc.robot;

import java.util.*;

import edu.wpi.first.wpilibj.Joystick;
import edu.wpi.first.wpilibj.Compressor;
import edu.wpi.first.wpilibj.TimedRobot;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.interfaces.Gyro;
import edu.wpi.first.wpilibj.AnalogGyro;

import org.opencv.imgproc.*;
import org.opencv.core.*;

import edu.wpi.cscore.CvSink;
import edu.wpi.cscore.CvSource;
import edu.wpi.cscore.UsbCamera;
import edu.wpi.first.cameraserver.CameraServer;

import frc.robot.RobotMap;
import frc.robot.commands.*;

public class Robot extends TimedRobot {
  double drivingAngle=0;
  int distance;

  //pneumatics
  boolean pneumatics_boot=false, pneumatics_move=false;

  private Joystick mstick = new Joystick(RobotMap.joystickPort);
  private Gyro mgyro = new AnalogGyro(1);
  private final Timer mtimer = new Timer();

  // vision
  Thread m_visionThread;
  Mat hsv = new Mat();
  Mat Kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(10, 10));

  boolean is_ball_find = false;
  double ball_width, ball_x, ball_y;

  @Override
  public void robotInit() {
    m_visionThread = new Thread(() -> {
      UsbCamera camera = CameraServer.getInstance().startAutomaticCapture();
      camera.setResolution(640, 480);
      CvSink cvSink = CameraServer.getInstance().getVideo();
      CvSource outputStream = CameraServer.getInstance().putVideo("frame", 640, 480);
      while (!Thread.interrupted()) {
        Mat frame = new Mat();
        if (cvSink.grabFrame(frame) == 0) {
          outputStream.notifyError(cvSink.getError());
          continue;
        }
        Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_BGR2HSV);
        Core.inRange(hsv, new Scalar(3,170,150), new Scalar(10,240,255), hsv);
        Imgproc.erode(hsv, hsv, Kernel);
        Imgproc.dilate(hsv, hsv, Kernel);
        
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(hsv, contours, new Mat(), Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
        Rect rect = null;
        double maxArea = 400;

        for(int i=0; i<contours.size(); i++){
          Mat cnt = contours.get(i);
          double contourArea = Imgproc.contourArea(cnt);
          if(contourArea > maxArea){
            rect = Imgproc.boundingRect(contours.get(i));
            maxArea = contourArea;
          }
        }
        if(rect==null){
          is_ball_find=false;
        }
        else{
          is_ball_find=true;
          ball_width = rect.width;
          ball_x = rect.x + rect.width/2;
          //ball_y = rect.y + rect.height/2;
          Imgproc.rectangle(frame, new Point(rect.x, rect.y), new Point(rect.x+rect.width, rect.y+rect.height),
              new Scalar(255, 255, 255), 3);
          distance = 33*640/rect.width;
          drivingAngle = Math.toDegrees(Math.atan((ball_x-320)/distance));
        }
        outputStream.putFrame(frame);
      }
    });
    m_visionThread.setDaemon(true);
  }

  @Override
  public void autonomousInit() {
    m_visionThread.start();
    mtimer.reset();
    mtimer.start();
  }

  @Override
  public void autonomousPeriodic() {
    
    if(is_ball_find){
      //when the direct is not precise
      if(Math.abs(drivingAngle)>3){
        MecanumDriver.rotate((int)Math.signum(drivingAngle));
      }
      //when the direct is precise
      else{
        //when the distance is too long
        if(distance>5){
          MecanumDriver.drive(0.5, drivingAngle);
        }
        //when approach the object, grab it
        else{
          MecanumDriver.stop();
        }
      }
    }
    if (mtimer.get() < 5.0) {
      MecanumDriver.drive(1, 0, 0);
    }
    
  }

  @Override
  public void teleopInit() {
    m_visionThread.interrupt();
    pneumatics_boot=false;
    pneumatics_move=false;
  }

  @Override
  public void teleopPeriodic() {
    MecanumDriver.drive(mstick.getX()*0.1, -mstick.getY()*0.1, 0);
    //MecanumDriver.drive(mstick.getRawAxis(4)*0.1, -mstick.getY()*0.1, 0, mgyro.getAngle());
    if(mstick.getRawButton(5)){
      MecanumDriver.rotate(1);
    }
    else if(mstick.getRawButton(6)){
      MecanumDriver.rotate(-1);
    }

    if(mstick.getRawButton(1)){
      Grab.move_back();
    }
    else if(mstick.getRawButtonPressed(4)){
      Grab.out();
    }
    else if(mstick.getRawButton(9)){
      Grab.move_forth();
    }
    else{
      Grab.stop();
    }

    if(mstick.getRawButtonPressed(3)){
      Pneumatics.boot(pneumatics_boot);
      pneumatics_boot=!pneumatics_boot;
    }
    if(mstick.getRawButtonPressed(2)){
      Pneumatics.move(pneumatics_move);
      pneumatics_move=!pneumatics_move;
    }
  }
}