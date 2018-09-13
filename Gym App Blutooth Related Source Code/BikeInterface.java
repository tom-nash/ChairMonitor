package com.ryan.gymapp.Interfaces;
import android.provider.ContactsContract;
import android.renderscript.Element;
import android.util.Log;

import com.jjoe64.graphview.series.DataPoint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Queue;

import static android.icu.lang.UCharacter.GraphemeClusterBreak.T;

/**
 * Created by ryan_ on 3/12/2017.
 */

public class BikeInterface extends Machine {
    // All data points to be calculated from the HashMap data
    private double rpmPeak;
    private double voltagePeak;
    private double currentPeak;
    private double velocityPeak;
    private double distanceTotal;
    private double velocityAvg;
    private final int[] dataTypes = {DataType.ALTERNATOR_CURRENT, DataType.ENCODER_RPM, DataType.ALTERNATOR_CURRENT,
            DataType.VELOCITY, DataType.DISTANCE};
    private Double scaler = 3.6e12;


    @Override
    protected void initializeData() {
        for (int type : dataTypes){
            rawData.put(type, new ArrayList<DataPoint>());
            rawData.get(type).add(new DataPoint(0.0, 0.0));
        }
    }

    @Override
    public String machineType(){
        return "Bike";
    }

    /**
     * Used for parsing data values into a HashMap with arrays instead of lists
     */
    @Override
    public HashMap<Integer, DataPoint[]> parseData() {
    // why do I need this method?
        levelLists();
        HashMap<Integer, DataPoint[]> parsedData = new HashMap<>();
        int length = rawData.get(DataType.ENCODER_RPM).size();
        DataPoint[] rpmData = new DataPoint[length];
        DataPoint[] voltageData = new DataPoint[length];
        DataPoint[] currentData = new DataPoint[length];
        DataPoint[] distanceData = new DataPoint[length];
        DataPoint[] velocityData = new DataPoint[length];

        for (int j = 0; j < dataTypes.length; j++) {
            DataPoint[] data = new DataPoint[length];
            for (int i = 0; i < length; i++) {
                data[i] = rawData.get(j).get(i);
            }
            parsedData.put(j, data);
        }

        parsedData.put(DataType.ENCODER_RPM, rpmData);
        parsedData.put(DataType.ALTERNATOR_VOLTAGE, voltageData);
        parsedData.put(DataType.ALTERNATOR_CURRENT, currentData);
        parsedData.put(DataType.DISTANCE, distanceData);
        parsedData.put(DataType.VELOCITY, velocityData);
        parsedData.keySet();

        return parsedData;
    }


    /**
     * Constructor for BikeInterface
     * Initializes variables
     */
    public BikeInterface() {
        initializeData();
    }

    /**
     * Used for parsing a single data point into a HashMap
     */
    @Override
    public void addData(Integer dataType, DataPoint dataPoint){
        rawData.get(dataType).add(dataPoint);
    }

    /**
     * Returns an array of data calculated from the raw data
     * @return Array of statistical data
     */
    public Double[] getData(){
        return new Double[] {rpmPeak, voltagePeak,
                currentPeak, distanceTotal,
                velocityAvg, velocityPeak};
    }

    /**
     * returns currentPeak
     * @return peak value of the current data
     */
    public Double getCurrentPeak() {
        return currentPeak;
    }

    /**
     * returns distanceTotal
     * @return sum of the distance data
     */
    public Double getDistanceTotal() {
        return distanceTotal;
    }

    /**
     * returns rpmPeak
     * @return peak value of the rpm data
     */
    public Double getRpmPeak() {
        return rpmPeak;
    }

    /**
     * returns velocityAvg
     * @return average value of the velocity data
     */
    public Double getVelocityAvg() {
        return velocityAvg;
    }

    /**
     * returns velocityPeak
     * @return peak value of the velocity data
     */
    public Double getVelocityPeak() {
        return velocityPeak;
    }

    /**
     * returns voltagePeak
     * @return peak value of the voltage data
     */
    public Double getVoltagePeak() {
        return voltagePeak;
    }

    /**
     * Calculates useful statistics based on the stored data
     */
    public void calculateData(){
        levelLists();

      if (rawData.get(DataType.ENCODER_RPM).size() > 0){
          List<DataPoint> RpmList = rawData.get(DataType.VELOCITY);
          rpmPeak = 0.0;
          voltagePeak = 0.0;
          currentPeak = 0.0;

          for (int i = 0; i < RpmList.size(); i++) {
              DataPoint rpmPoint = rawData.get(DataType.ENCODER_RPM).get(i);
              DataPoint velocityPoint = rawData.get(DataType.VELOCITY).get(i);
              DataPoint distancePoint = rawData.get(DataType.DISTANCE).get(i);
              if (rpmPoint.getY() > rpmPeak) {
                  rpmPeak = rpmPoint.getY();
              }
              if(velocityPoint != null){
                  if (velocityPoint.getY() > velocityPeak){
                      velocityPeak = velocityPoint.getY();
                  }
                  velocityAvg += velocityPoint.getY();
              }
              distanceTotal += distancePoint.getY();
          }
          DataPoint lastPoint = rawData.get(DataType.VELOCITY).get(RpmList.size()-1);
          velocityAvg = velocityAvg/(lastPoint.getX());
      }
    }

    /**
     * Checks if all data arrays are the same size
     * @return Boolean true if all arrays are the same size otherwise false
     */
    private Boolean safeCheckData(){
        for (int i = 0; i < dataTypes.length - 1; i++){
            if (rawData.get(dataTypes[i]).size() != rawData.get(dataTypes[i + 1]).size()) {
                return false;
            }
        }
        return true;
    }


    /**
     * Removes elements of the data arrays so that each data arrays is the same length
     * as the array with the minimum length. The last elements are removed from the array.
     */
    private void levelLists(){
        if (!safeCheckData()) {
            int velocityLength = rawData.get(DataType.VELOCITY).size();
            int rpmLength = rawData.get(DataType.ENCODER_RPM).size();
            int distanceLength = rawData.get(DataType.DISTANCE).size();
            Log.d("hi", rpmLength+", "+distanceLength+", "+velocityLength);
            int min = rawData.get(0).size();
            int max = rawData.get(0).size();
            for (int i = 1; i < dataTypes.length; i++) {
                int length = rawData.get(i).size();
                if (length < min) {
                    min = length;
                }
                if (length > max) {
                    max = length;
                }
            }
            for (int type = 0; type < dataTypes.length; type++) {
                for (int index = min; index < max; index++) {
                    // size is greater than min removes last element
                    if (rawData.get(type).size() > min) {
                        rawData.get(type).remove(rawData.get(type).size() - 1);
                    }
                }
            }
        }
    }

}