import com.fazecast.jSerialComm.*;
import java.util.*;
import java.io.*;

//Make sure raspberry pi has jar file and wpilib library
//Probably need some more libraries lol

public class Raspberry {
    //This is just for recording ports, remove once all ports are matched up
    SerialPort[] thePorts = SerialPort.getCommPorts();
    AprilTagFieldLayout theLayout;
    ArrayList<AprilTag> theTags;

    //Gives description for each SP, mark them down and then trial and error
    for(SerialPort aPort: thePorts) {
        System.out.println(aPort.getPortDescription());
    }

    Thread vision;
    CvSink topSink = CameraServer.getVideo("Top");
    CvSink bottomSink = CameraServer.getVideo("Bottom");
    //Allows more cameras to be for better detection
    ArrayList<CvSink> mySinks = new ArrayList<CvSink>();
    //Gives the angle of the camera relative to the frame
    //If angled left of the arm, then angle will be greater than 180
    //If angle right of the arm, then the angle will be less than 180
    //If parallel to the arm, then the angle is 0
    //If directly away from the arm, then the angle is 180
    ArrayList<Double> camAnglesX = new ArrayList<Double>();
    ArrayList<Double> camAnglesY = new ArrayList<Double>();
    ArrayList<Double> yResolution = new ArrayList<Double>();
    ArrayList<Double> perPixelAngleY = new ArrayList<Double>();
    ArrayList<Double> xResolution = new ArrayList<Double>();
    ArrayList<Double> perPixelAngleX = new ArrayList<Double>();
    AprilTagDetector myDetector = new AprilTagDetector();
    AprilTagDetection[] myTags;
    Mat img;

    //Need to put correct port descriptor as parameter
    //Set update rate to 50 to start out
    SerialPort accelerometer1 = new SerialPort();
    SerialPort accelerometer2 = new SerialPort();
    Thread accel1;
    Thread accel2;
    double timeOne = Timer.getFPGATimestamp();
    double timeTwo = Timer.getFPGATimestamp();
    boolean oneAccel;
    boolean oneAngle;
    boolean twoAccel;
    boolean twoAngle;
    //accelerometer1, accelerometer2, average
    double[] accelX = new double[3];
    double[] accelY = new double[3];
    double[] accelZ = new double[3];
    double[] velocityX = new double[3];
    double[] velocityY = new double[3];
    double[] velocityZ = new double[3];
    double[] positionX = new double[3];
    double[] positionY = new double[3];
    double[] positionZ = new double[3];
    double[] angularAccel = new double[3];
    double[] angularVelocity = new double[3];
    double[] angle = new double[3];

    static final byte exclaimASK = 33;
    static final byte poundASK = 35;
    static final byte yASK = 121;
    static final byte pASK = 112;
    static final byte newLineBYTE = 0x13;

    public Raspberry() {
        NetworkTable.setTeam(3692);
        myDetector.addFamily("16h5");
        mySinks.add(topSink);
        mySinks.add(bottomSink);

        try {
            theLayout = new AprilTagFieldLayout("fieldLayout.json");
            theTags = theLayout.getTags();

        } catch(IOException e) {

        }

        //Top camera specs, then bottom camera specs
        camAnglesX.add(0.0);
        camAnglesX.add(0.0);
        camAnglesY.add(0.0);
        camAnglesY.add(0.0);
        yResolution.add(0.0);
        yResolution.add(0.0);
        perPixelAngleY.add(0.0);
        perPixelAngleY.add(0.0);
        xResolution.add(0.0);
        xResolution.add(0.0);
        perPixelAngleX.add(0.0);
        perPixelAngleX.add(0.0);

        accel1 = new Thread(() -> {
            //Need to use byte updating
            byte[] msg = new byte[1];

            while(msg[0] != exclaimASK) {
                if(accelerometer1.bytesAvailable() > 0) {
                    accelerometer1.readBytes(msg, msg.length);
                } else {
                    try {
                        Thread.sleep(1);
                    } catch(InterruptedException e) {

                    }
                }
            }

            while(accelerometer1.bytesAvailable() == 0) {
                try {
                    Thread.sleep(1);
                } catch(InterruptedException e) {

                }
            }

            accelerometer1.readBytes(msg, msg.length);

            if(msg[0] == yASK) {
                //Message in ascii

                msg = new byte[8];

                while(accelerometer1.bytesAvailable() < 8) {
                    try {
                        Thread.sleep(1);
                        } catch(InterruptedException e) {

                    }
                }

                accelerometer1.readBytes(msg, msg.length);

                ByteBuffer floater = ByteBuffer.wrap(msg);
                float tempAngle = floater.getFloat();

                angularAccel[0] = SmartDashboard.getNumber("Angular Acceleration", angularAccel[2]);
                angularVelocity[0] = SmartDashboard.getNumber("Angular Velocity", angularVelocity[2]);
                angle[0] = SmartDashboard.getNumber("Angle", angle[2]);
                
                double prevAngle = angle[0];
                double prevAngularVelocity = angularVelocity[0];
                angle[0] = tempAngle.doubleValue();

                if(angle[0] < 0) {
                    angle[0] += 360;
                }

                if(prevAngle > 180 && angle[0] < 180) {
                    angularVelocity[0] = ((angle[0] + 360) - prevAngle) / (Timer.getFPGATimestamp() - timeOne);
                } else if(prevAngle < 180 && angle[0] > 180) {
                    angularVelocity[0] = (angle[0] - (prevAngle + 360)) / (Timer.getFPGATimestamp() - timeOne);
                } else {
                    angularVelocity[0] = (angle[0] - prevAngle) / (Timer.getFPGATimestamp() - timeOne);
                }

                angularAccel[0] = (angularVelocity[0] - prevAngularVelocity) / (Timer.getFPGATimestamp() - timeOne);

                timeOne = Timer.getFPGATimestamp();
                oneAngle = true;
            } else if(msg[0] == poundASK) {
                while(accelerometer1.bytesAvailable() < 2) {
                    try {
                        Thread.sleep(1);
                    } catch(InterruptedException e) {

                    }
                }

                accelerometer1.readBytes(msg, msg.length);
                accelerometer1.readBytes(msg, msg.length);

                if(msg[0] == pASK) {
                    //Get the accelerometer data
                    while(accelerometer1.bytesAvailable() < 44) {
                        try {
                            Thread.sleep(1);
                        } catch(InterruptedException e) {

                        }
                    }

                    msg = new byte[44];

                    accelerometer1.readBytes(msg, msg.length);

                    ByteOrder little = ByteOrder.LITTLE_ENDIAN;
                    ByteBuffer helper = ByteBuffer.wrap(msg, 14, 2);
                    helper = helper.order(little);
                    accelX = (double) (helper.getInt() / 1000.0);
                    helper = ByteBuffer.wrap(msg, 16, 2);
                    helper = helper.order(little);
                    accelY = (double) (helper.getInt() / 1000.0);
                    helper = ByteBuffer.wrap(msg, 18, 2);
                    helper = helper.order(little);
                    accelZ = (double) (helper.getInt() / 1000.0);
                    helper = ByteBuffer.wrap(msg, 20, 4);
                    helper = helper.order(little);
                    velocityX = helper.getShort().doubleValue();
                    helper = ByteBuffer.wrap(msg, 24, 4);
                    helper = helper.order(little);
                    velocityY = helper.getShort().doubleValue();
                    helper = ByteBuffer.wrap(msg, 28, 4);
                    helper = helper.order(little);
                    velocityZ = helper.getShort().doubleValue();
                    helper = ByteBuffer.wrap(msg, 32, 4);
                    helper = helper.order(little);
                    positionX = helper.getShort().doubleValue();
                    helper = ByteBuffer.wrap(msg, 36, 4);
                    helper = helper.order(little);
                    positionY = helper.getShort().doubleValue();
                    helper = ByteBuffer.wrap(msg, 40, 4);
                    helper = helper.order(little);
                    positionZ = helper.getShort().doubleValue();
                    
                    oneAccel = true;
                }
            }

            msg = new byte[1];

            while(msg[0] != newLineBYTE) {
                while(accelerometer1.bytesAvailable() == 0) {
                    try {
                        Thread.sleep(1);
                    } catch(InterruptedException e) {

                    }
                }

                accelerometer1.readBytes(msg, msg.length);
            }
            
            accelX[0] = SmartDashboard.getNumber("Acceleration X", accelX[2]);
            accelY[0] = SmartDashboard.getNumber("Acceleration Y", accelY[2]);
            accelZ[0] = SmartDashboard.getNumber("Acceleration Z", accelZ[2]);
            velocityX[0] = SmartDashboard.getNumber("Velocity X", velocityX[2]);
            velocityY[0] = SmartDashboard.getNumber("Velocity Y", velocityY[2]);
            velocityZ[0] = SmartDashboard.getNumber("Velocity Z", velocityZ[2]);
            positionX[0] = SmartDashboard.getNumber("Position X", positionX[2]);
            positionY[0] = SmartDashboard.getNumber("Position Y", positionY[2]);
            positionZ[0] = SmartDashboard.getNumber("Position Z", positionZ[2]);

            if(onceAccel && oneAngle && twoAccel && twoAngle) {
                oneAccel = false;
                oneAngle = false;
                twoAccel = false;
                twoAngle = false;

                accelX[2] = (accelX[0] + accelX[1]) / 2.0;
                accelY[2] = (accelY[0] + accelY[1]) / 2.0;
                accelZ[2] = (accelZ[0] + accelZ[1]) / 2.0;
                velocityX[2] = (velocityX[0] + velocityX[1]) / 2.0;
                velocityY[2] = (velocityY[0] + velocityY[1]) / 2.0;
                velocityZ[2] = (velocityZ[0] + velocityZ[1]) / 2.0;
                positionX[2] = (positionX[0] + positionX[1]) / 2.0;
                positionY[2] = (positionY[0] + positionY[1]) / 2.0;
                positionZ[2] = (positionZ[0] + positionZ[1]) / 2.0;
                angularAccel[2] = (angularAccel[0] + angularAccel[1]) / 2.0;
                angularVelocity[2] = (angularVelocity[0] + angularVelocity[1]) / 2.0;
                angle[2] = (angle[0] + angle[1]) / 2.0;

                //Update Network Tables
                SmartDashboard.putNumber("Acceleration X", accelX[2]);
                SmartDashboard.putNumber("Acceleration Y", accelY[2]);
                SmartDashboard.putNumber("Acceleration Z", accelZ[2]);
                SmartDashboard.putNumber("Velocity X", velocityX[2]);
                SmartDashboard.putNumber("Velocity Y", velocityY[2]);
                SmartDashboard.putNumber("Velocity Z", velocityZ[2]);
                SmartDashboard.putNumber("Position X", positionX[2]);
                SmartDashboard.putNumber("Position Y", positionY[2]);
                SmartDashboard.putNumber("Position Z", positionZ[2]);
                SmartDashboard.putNumber("Angular Acceleration", angularAccel[2]);
                SmartDashboard.putNumber("Angular Velocity", angularVelocity[2]);
                SmartDashboard.putNumber("Angle", angle[2]);
            }
            
            try {
                Thread.sleep(25);
            } catch(InterruptedException e) {
                
            }
        });
        accel1.setDaemon(true);
        accel1.start();

        accel2 = new Thread(() -> {
            //Need to use byte updating
            byte[] msg = new byte[1];

            while(msg[0] != exclaimASK) {
                if(accelerometer2.bytesAvailable() > 0) {
                    accelerometer2.readBytes(msg, msg.length);
                } else {
                    try {
                        Thread.sleep(1);
                    } catch(InterruptedException e) {

                    }
                }
            }

            while(accelerometer2.bytesAvailable() == 0) {
                try {
                    Thread.sleep(1);
                } catch(InterruptedException e) {

                }
            }

            accelerometer2.readBytes(msg, msg.length);

            if(msg[0] == yASK) {
                //Message in ascii

                msg = new byte[8];

                while(accelerometer2.bytesAvailable() < 8) {
                    try {
                        Thread.sleep(1);
                        } catch(InterruptedException e) {

                    }
                }

                accelerometer2.readBytes(msg, msg.length);

                ByteBuffer floater = ByteBuffer.wrap(msg);
                float tempAngle = floater.getFloat();

                angularAccel[1] = SmartDashboard.getNumber("Angular Acceleration", angularAccel[2]);
                angularVelocity[1] = SmartDashboard.getNumber("Angular Velocity", angularVelocity[2]);
                angle[1] = SmartDashboard.getNumber("Angle", angle[2]);
                
                double prevAngle = angle[1];
                double prevAngularVelocity = angularVelocity[0];
                angle[1] = tempAngle.doubleValue();

                if(angle[1] < 0) {
                    angle[1] += 360;
                }

                if(prevAngle > 180 && angle[1] < 180) {
                    angularVelocity[1] = ((angle[1] + 360) - prevAngle) / (Timer.getFPGATimestamp() - timeTwo);
                } else if(prevAngle < 180 && angle[1] > 180) {
                    angularVelocity[1] = (angle[1] - (prevAngle + 360)) / (Timer.getFPGATimestamp() - timeTwo);
                } else {
                    angularVelocity[1] = (angle[1] - prevAngle) / (Timer.getFPGATimestamp() - timeTwo);
                }

                angularAccel[1] = (angularVelocity[1] - prevAngularVelocity) / (Timer.getFPGATimestamp() - timeTwo);

                timeTwo = Timer.getFPGATimestamp();
                twoAngle = true;
            } else if(msg[0] == poundASK) {
                while(accelerometer2.bytesAvailable() < 2) {
                    try {
                        Thread.sleep(1);
                    } catch(InterruptedException e) {

                    }
                }

                accelerometer2.readBytes(msg, msg.length);
                accelerometer2.readBytes(msg, msg.length);

                if(msg[0] == pASK) {
                    //Get the accelerometer data
                    while(accelerometer2.bytesAvailable() < 44) {
                        try {
                            Thread.sleep(1);
                        } catch(InterruptedException e) {

                        }
                    }

                    msg = new byte[44];

                    accelerometer2.readBytes(msg, msg.length);

                    ByteOrder little = ByteOrder.LITTLE_ENDIAN;
                    ByteBuffer helper = ByteBuffer.wrap(msg, 14, 2);
                    helper = helper.order(little);
                    accelX[1] = (double) (helper.getInt() / 1000.0);
                    helper = ByteBuffer.wrap(msg, 16, 2);
                    helper = helper.order(little);
                    accelY[1] = (double) (helper.getInt() / 1000.0);
                    helper = ByteBuffer.wrap(msg, 18, 2);
                    helper = helper.order(little);
                    accelZ[1] = (double) (helper.getInt() / 1000.0);
                    helper = ByteBuffer.wrap(msg, 20, 4);
                    helper = helper.order(little);
                    velocityX[1] = helper.getShort().doubleValue();
                    helper = ByteBuffer.wrap(msg, 24, 4);
                    helper = helper.order(little);
                    velocityY[1] = helper.getShort().doubleValue();
                    helper = ByteBuffer.wrap(msg, 28, 4);
                    helper = helper.order(little);
                    velocityZ[1] = helper.getShort().doubleValue();
                    helper = ByteBuffer.wrap(msg, 32, 4);
                    helper = helper.order(little);
                    positionX[1] = helper.getShort().doubleValue();
                    helper = ByteBuffer.wrap(msg, 36, 4);
                    helper = helper.order(little);
                    positionY[1] = helper.getShort().doubleValue();
                    helper = ByteBuffer.wrap(msg, 40, 4);
                    helper = helper.order(little);
                    positionZ[1] = helper.getShort().doubleValue();
                    
                    twoAccel = true;
                }
            }

            msg = new byte[1];

            while(msg[0] != newLineBYTE) {
                while(accelerometer2.bytesAvailable() == 0) {
                    try {
                        Thread.sleep(1);
                    } catch(InterruptedException e) {

                    }
                }

                accelerometer2.readBytes(msg, msg.length);
            }
            
            accelX[1] = SmartDashboard.getNumber("Acceleration X", accelX[2]);
            accelY[1] = SmartDashboard.getNumber("Acceleration Y", accelY[2]);
            accelZ[1] = SmartDashboard.getNumber("Acceleration Z", accelZ[2]);
            velocityX[1] = SmartDashboard.getNumber("Velocity X", velocityX[2]);
            velocityY[1] = SmartDashboard.getNumber("Velocity Y", velocityY[2]);
            velocityZ[1] = SmartDashboard.getNumber("Velocity Z", velocityZ[2]);
            positionX[1] = SmartDashboard.getNumber("Position X", positionX[2]);
            positionY[1] = SmartDashboard.getNumber("Position Y", positionY[2]);
            positionZ[1] = SmartDashboard.getNumber("Position Z", positionZ[2]);

            if(onceAccel && oneAngle && twoAccel && twoAngle) {
                oneAccel = false;
                oneAngle = false;
                twoAccel = false;
                twoAngle = false;

                accelX[2] = (accelX[0] + accelX[1]) / 2.0;
                accelY[2] = (accelY[0] + accelY[1]) / 2.0;
                accelZ[2] = (accelZ[0] + accelZ[1]) / 2.0;
                velocityX[2] = (velocityX[0] + velocityX[1]) / 2.0;
                velocityY[2] = (velocityY[0] + velocityY[1]) / 2.0;
                velocityZ[2] = (velocityZ[0] + velocityZ[1]) / 2.0;
                positionX[2] = (positionX[0] + positionX[1]) / 2.0;
                positionY[2] = (positionY[0] + positionY[1]) / 2.0;
                positionZ[2] = (positionZ[0] + positionZ[1]) / 2.0;
                angularAccel[2] = (angularAccel[0] + angularAccel[1]) / 2.0;
                angularVelocity[2] = (angularVelocity[0] + angularVelocity[1]) / 2.0;
                angle[2] = (angle[0] + angle[1]) / 2.0;

                //Update Network Tables
                SmartDashboard.putNumber("Acceleration X", accelX[2]);
                SmartDashboard.putNumber("Acceleration Y", accelY[2]);
                SmartDashboard.putNumber("Acceleration Z", accelZ[2]);
                SmartDashboard.putNumber("Velocity X", velocityX[2]);
                SmartDashboard.putNumber("Velocity Y", velocityY[2]);
                SmartDashboard.putNumber("Velocity Z", velocityZ[2]);
                SmartDashboard.putNumber("Position X", positionX[2]);
                SmartDashboard.putNumber("Position Y", positionY[2]);
                SmartDashboard.putNumber("Position Z", positionZ[2]);
                SmartDashboard.putNumber("Angular Acceleration", angularAccel[2]);
                SmartDashboard.putNumber("Angular Velocity", angularVelocity[2]);
                SmartDashboard.putNumber("Angle", angle[2]);
            }
            
            try {
                Thread.sleep(25);
            } catch(InterruptedException e) {
                
            }
        });
        accel2.setDaemon(true);
        accel2.start();

        vision = new Thread(() -> {
            double totalX = 0.0;
            double totalY = 0.0;

            for(int i = 0; i < mySink.size(); i++) {
                mySink.get(i).grabFrame(img);

                myTags = myDetector.detect(img);

                double detectorX = 0.0;
                double detectorY = 0.0;
                //Ignore Z, we don't use it

                for(AprilTagDetection aTag: myTags) {
                    AprilTag currTag = theTags.get(aTag.getId() - 1);

                    //Tag length divided by pixel length of corners to get a per pixel length
                    double xPixelLength = 0.1524 / Math.abs(currTag.getCornerX(0) - currTag.getCornerX(1));
                    double yPixelLength = 0.1524 / Math.abs(currTag.getCornerY(1) - currTag.getCornerY(2));
                    
                    //Since we know the position of the center of the tag, this is the easiest to use
                    double yAngle = Math.abs(currTag.getCornerY(0) - currTag.getCenterY()) * perPixelAngleY;
                    double xAngle = Math.abs(currTag.getCornerX(0) - currTag.getCenterY()) * perPixelAngleY;
                    
                    //Same length from center to perpendicular perimeter for all of them (3" to metres).
                    double yLength = 0.0762;
                    double xLength = 0.0762;
                }
            }

            if(totalX != 0.0) {
                positionX = totalX;
            }
            if(totalY != 0.0) {
                positionY = totalY;
            }
            
            try {
                Thread.sleep(25);
            } catch(InterruptedException e) {
                
            }
        });
        vision.setDaemon(true);
        vision.start();
    }
}
