public class Constants
{
    public static final double RampUp = 2;

    public static final double accelProportion = 1.0;
    //Position of game piece to pick up from station
    //index 0 == station 1, index 1 == 2, and index 2 == 3
    public static final double[] autoPieceX = new double[] {0.0, 0.0, 0.0};
    public static final double[] autoPieceZ = new double[] {0.0, 0.0, 0.0};
    //Position in front of teeter totter to go to
    public static final double autoTeeterX = 0.0;
    public static final double autoTeeterZ = 0.0;
    //Teeter midline, might be Z instead
    public static final double teeterPositionX = 0.0;

    //The amount of acceleration turning the motors to 1 gives
    public static final double accelConst = 1.0;
    //The maximum velocity the robot can travel in ft/s
    public static final double maxVelocity = 1.0;
    //Radius of the robot for the angle alignment (feet)
    public static final double robotRadius = 2.0;

    //Out-of-Bounds---Stores the X ranges and Z ranges of areas we don't want the robot to go to.
    //Each row stores two ranges---The lowest at index 0, and the highest at index 1
    public static final double[][] OBX = new double[][] {{}};
    public static final double[][] OBZ = new double[][] {{}};

    //Under ideal cirumstances these are arrays relating to the OB arrays, but this should suffice for this year's game
    //Clearance around an obstacle
    public static final double xFeetClearance = 1.0;
    //Clearance before an obstacle
    public static final double zFeetClearance = 1.0;

    //The amount the position can be off by at any point of alignment on the curve
    public static final double xCurveTolerance = 0.1;
    public static final double zCurveTolerance = 0.1;

    //The amount the position can be off of the target position
    public static final double zPositionTolerance = 0.05;
    public static final double xPositionTolerance = 0.05;
    public static final double positionTolerance = 0.05;

    //Stores coordinates of teeter totter (since while it can be navigated, it requires correct positioning)
    public static final double[] teeterCorners = new double[] {};
}