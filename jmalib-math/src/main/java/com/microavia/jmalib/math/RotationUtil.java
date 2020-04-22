package com.microavia.jmalib.math;

import java.util.Arrays;

import static java.lang.Math.*;

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

    public static double[] quaternionByAngleAxis(double[] angleAxis) {
        double a = sqrt(angleAxis[0] * angleAxis[0] + angleAxis[1] * angleAxis[1] + angleAxis[2] * angleAxis[2]);
        if (a > 1.0e-10) {
            double b = sin(a / 2) / a;
            return new double[]{cos(a / 2), angleAxis[0] * b, angleAxis[1] * b, angleAxis[2] * b};
        } else {
            return new double[]{1, 0, 0, 0};
        }
    }

    public static double[] quaternionByEulerAngles(double roll, double pitch, double yaw) {
        double[] q = quaternionByAngleAxis(new double[]{0, 0, yaw});
        q = quaternionMult(quaternionByAngleAxis(new double[]{0, pitch, 0}), q);
        return quaternionMult(quaternionByAngleAxis(new double[]{roll, 0, 0}), q);
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

    public static double[] rotateByQuaternion(double[] v, double[] q) {
        double[] qConj = new double[]{q[0], -q[1], -q[2], -q[3]};
        double[] qV = new double[]{0, v[0], v[1], v[2]};
        return Arrays.copyOfRange(quaternionMult(quaternionMult(q, qV), qConj), 1, 4);
    }

    public static double[] quaternionMult(double[] q1, double[] q2) {
        return new double[]{
                q1[0] * q2[0] - q1[1] * q2[1] - q1[2] * q2[2] - q1[3] * q2[3],
                q1[0] * q2[1] + q1[1] * q2[0] + q1[2] * q2[3] - q1[3] * q2[2],
                q1[0] * q2[2] + q1[2] * q2[0] + q1[3] * q2[1] - q1[1] * q2[3],
                q1[0] * q2[3] + q1[3] * q2[0] + q1[1] * q2[2] - q1[2] * q2[1],
        };
    }
}
