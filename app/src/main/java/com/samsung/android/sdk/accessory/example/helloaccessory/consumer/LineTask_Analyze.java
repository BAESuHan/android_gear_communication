package com.samsung.android.sdk.accessory.example.helloaccessory.consumer;

import java.util.ArrayList;

import marytts.signalproc.adaptation.smoothing.TemporalSmoother;

public class LineTask_Analyze {
    public ConsumerService mConsumerService = null;


    public  double[] rawSum(double[] x ,double[] y,double[] z ){
        double[] return_value = new double[x.length];

        for(int i=0;i<x.length;i++){
            return_value[i] = x[i]+y[i]+z[i];
        }

        return return_value;
    }

    public double[] smoothSum(double[] rawSum){
        double[] return_value = new double[rawSum.length];
        return_value=TemporalSmoother.smooth(rawSum,15);

        return return_value;
    }


    public double[] countFingerNose(double[] smoothSum){
        double[] return_value =new double[smoothSum.length/2];
        int j=0;
        for(int i=1;i<smoothSum.length-1;i++){
            if(smoothSum[i-1] <= smoothSum[i] && smoothSum[i] >= smoothSum[i+1]){
                if(smoothSum[i]>0) {
                    return_value[j++] = smoothSum[i];
                }
            }
        }
        return return_value;
    }


    public double[] countTurnHand(double[] smoothSum){
        double[] return_value =new double[smoothSum.length/2];
        int j=0;
        for(int i=1;i<smoothSum.length-1;i++){
            if(smoothSum[i-1] >= smoothSum[i] && smoothSum[i] <= smoothSum[i+1]){
                return_value[j++] = smoothSum[i];
            }
        }
        return return_value;
    }



}
