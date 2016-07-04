package in.ac.iitb.cse.carts.safestreet.automatedDetection;

import java.util.Date;

public class AccelerometerData {

    double x;
    double y;
    double z;
    Date time;
    double latitude;
    double longitude;
    double speed;

    public AccelerometerData(double x, double y, double z, Date time, double latitude, double longitude, double speed) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.time = time;
        this.latitude = latitude;
        this.longitude = longitude;
        this.speed = speed;
    }

    public double getSpeed() {
        return speed;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    @Override
    public String toString() {
        return "AccelerometerData{" +
                "x=" + x +
                ", y=" + y +
                ", z=" + z +
                ", time=" + time +
                '}';
    }

    public Date getTime() {
        return time;
    }
}
