package com.microavia.jmalib.math;

import static java.lang.Math.cos;
import static java.lang.Math.sin;

/**
 * User: ton Date: 02.06.13 Time: 20:20
 */
public class RotationUtil {
    public static double[] rotationMatrixByEulerAngles(double roll, double pitch, double yaw) {
        return new double[]{
                cos(pitch) * cos(yaw),
                sin(roll) * sin(pitch) * cos(yaw) - cos(roll) * sin(yaw),
                cos(roll) * sin(pitch) * cos(yaw) + sin(roll) * sin(yaw),
                cos(pitch) * sin(yaw),
                sin(roll) * sin(pitch) * sin(yaw) + cos(roll) * cos(yaw),
                cos(roll) * sin(pitch) * sin(yaw) - sin(roll) * cos(yaw),
                -sin(pitch),
                sin(roll) * cos(pitch),
                cos(roll) * cos(pitch)
        };
    }

    public static double[] rotationMatrixByQuaternion(double[] q) {
        double aSq = q[0] * q[0];
        double bSq = q[1] * q[1];
        double cSq = q[2] * q[2];
        double dSq = q[3] * q[3];
        return new double[]{
            aSq + bSq - cSq - dSq,
            2.0f * (q[1] * q[2] - q[0] * q[3]),
            2.0f * (q[0] * q[2] + q[1] * q[3]),
            2.0f * (q[1] * q[2] + q[0] * q[3]),
            aSq - bSq + cSq - dSq,
            2.0f * (q[2] * q[3] - q[0] * q[1]),
            2.0f * (q[1] * q[3] - q[0] * q[2]),
            2.0f * (q[0] * q[1] + q[2] * q[3]),
            aSq - bSq - cSq + dSq
        };
    }

    public static double[] eulerAnglesByQuaternion(double[] q) {
        return new double[]{
                Math.atan2(2.0 * (q[0] * q[1] + q[2] * q[3]), 1.0 - 2.0 * (q[1] * q[1] + q[2] * q[2])),
                Math.asin(2 * (q[0] * q[2] - q[3] * q[1])),
                Math.atan2(2.0 * (q[0] * q[3] + q[1] * q[2]), 1.0 - 2.0 * (q[2] * q[2] + q[3] * q[3])),
        };
    }
}
