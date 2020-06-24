/*
 * Copyright (c) 2015 Samsung Electronics Co., Ltd. All rights reserved. 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that 
 * the following conditions are met:
 * 
 *     * Redistributions of source code must retain the above copyright notice, 
 *       this list of conditions and the following disclaimer. 
 *     * Redistributions in binary form must reproduce the above copyright notice, 
 *       this list of conditions and the following disclaimer in the documentation and/or 
 *       other materials provided with the distribution. 
 *     * Neither the name of Samsung Electronics Co., Ltd. nor the names of its contributors may be used to endorse or 
 *       promote products derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.samsung.android.sdk.accessory.example.helloaccessory.consumer;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import biz.source_code.dsp.signal.EnvelopeDetector;
import edu.mines.jtk.dsp.BandPassFilter;
import edu.mines.jtk.dsp.Fft;
import edu.mines.jtk.dsp.HilbertTransformFilter;
import edu.mines.jtk.dsp.Sampling;


public class ConsumerActivity extends Activity {
    private static TextView mTextView;
    private static MessageAdapter mMessageAdapter;
    private boolean mIsBound = false;
    private ListView mMessageListView;
    private ConsumerService mConsumerService = null;
    public int DB_cnt;
    public float[] mean_xyz;
    public FirebaseDatabase database = FirebaseDatabase.getInstance();
    public DatabaseReference ref = database.getReference("gear");
    public LineTask_Analyze lineTask_analyze=new LineTask_Analyze();

    private static LineChart mChart;
    private Thread graphThread;
    private static boolean plotData = true;

//    private BandPassFilter BPF =new BandPassFilter(0.05,0.25,0.1,0.6);
//    private BandPassFilter LPF =new BandPassFilter(0.00,0.05,0.1,0.6);
//    private HilbertTransformFilter Hilbert = new HilbertTransformFilter(1000,0.01f,0.05f,0.25f);

    public double[] raw_sum;
    public double[] smooth_sum;
    public double[] cnt_task;
    public double[] copy_sum;
    static long start_time;
    static long end_time;
    public double task_time;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextView = (TextView) findViewById(R.id.tvStatus);
//        mMessageListView = (ListView) findViewById(R.id.lvMessage);
       //line 그리기
        mChart = (LineChart) findViewById(R.id.chart);
        mChart.getDescription().setEnabled(true);
        mChart.getDescription().setText("Real Time Falling Detection");
        mChart.setTouchEnabled(false);
        mChart.setDragEnabled(false);
        mChart.setScaleEnabled(false);
        mChart.setDrawGridBackground(true);
        mChart.setPinchZoom(false);
        mChart.setBackgroundColor(Color.WHITE);

        LineData data = new LineData();
        data.setValueTextColor(Color.WHITE);
        mChart.setData(data);
        startPlot();
        mMessageAdapter = new MessageAdapter();
        mMessageListView.setAdapter(mMessageAdapter);
        // Bind service
        mIsBound = bindService(new Intent(ConsumerActivity.this, ConsumerService.class), mConnection, Context.BIND_AUTO_CREATE);

    }

    @Override
    protected void onDestroy() {
        // Clean up connections
        if (mIsBound == true && mConsumerService != null) {
            if (mConsumerService.closeConnection() == false) {
                updateTextView("Disconnected");
                mMessageAdapter.clear();
            }
        }
        // Un-bind service
        if (mIsBound) {
            unbindService(mConnection);
            mIsBound = false;
        }
        super.onDestroy();
    }

    public void mOnClick(View v) {
        switch (v.getId()) {
            case R.id.buttonConnect: {
                if (mIsBound == true && mConsumerService != null) {
                    mConsumerService.findPeers();

                    clear();
                    start_time = System.currentTimeMillis();


                }
                break;
            }
            case R.id.buttonDisconnect: {
                if (mIsBound == true && mConsumerService != null) {
                    end_time = System.currentTimeMillis();

                    if(graphThread != null)
                        graphThread.interrupt();

                    if (mConsumerService.closeConnection() == false) {
                        updateTextView("Disconnected");
                        Toast.makeText(getApplicationContext(), R.string.ConnectionAlreadyDisconnected, Toast.LENGTH_LONG).show();
                        mMessageAdapter.clear();

                        ref.addValueEventListener(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                DB_cnt=(int)dataSnapshot.getChildrenCount();
                                task_time=end_time-start_time;
//                                raw_sum=lineTask_analyze.rawSum(ArratlistToArray(mConsumerService.d_x),ArratlistToArray(mConsumerService.d_y),ArratlistToArray(mConsumerService.d_z));
                                raw_sum=ArratlistToArray(mConsumerService.d_sum);
                                copy_sum=new double[raw_sum.length-19];
                                System.arraycopy(raw_sum,19,copy_sum,0,raw_sum.length-19);
                                smooth_sum = lineTask_analyze.smoothSum(copy_sum);
                                cnt_task=lineTask_analyze.countFingerNose(smooth_sum);
//                                Log.d("sdsdd ", String.valueOf(copy_sum.length));

                                ref.child("task").child("time").setValue(task_time/1000);
                                ref.child("task").child("sum").setValue(mConsumerService.d_sum);
//                                ref.child("task").child("rawsum").setValue(ArrayToArraylist(raw_sum));
                                ref.child("task").child("smoothsum").setValue(ArrayToArraylist(smooth_sum));
                                ref.child("task").child("cnttask").setValue(ArrayToArraylist(cnt_task));




                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError databaseError) {

                            }
                        });
                    }

                }
                break;
            }
            //이거는 없어도 될듯
            case R.id.buttonSend: {
                if (mIsBound == true && mConsumerService != null) {

//                    if (mConsumerService.sendData("Hello Accessory!")) {
//                    } else {
//                        Toast.makeText(getApplicationContext(), R.string.ConnectionAlreadyDisconnected, Toast.LENGTH_LONG).show();
//
//                    }
                    if (mConsumerService.closeConnection() == false){
                        end_time = System.currentTimeMillis();
                        if (mConsumerService.closeConnection() == false) {
                            updateTextView("Disconnected");
                            Toast.makeText(getApplicationContext(), R.string.ConnectionAlreadyDisconnected, Toast.LENGTH_LONG).show();
                            mMessageAdapter.clear();

                            ref.addValueEventListener(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                    DB_cnt=(int)dataSnapshot.getChildrenCount();
                                    task_time=end_time-start_time;
//                                raw_sum=lineTask_analyze.rawSum(ArratlistToArray(mConsumerService.d_x),ArratlistToArray(mConsumerService.d_y),ArratlistToArray(mConsumerService.d_z));
                                    raw_sum=ArratlistToArray(mConsumerService.d_sum);
                                    copy_sum=new double[raw_sum.length-19];
                                    System.arraycopy(raw_sum,19,copy_sum,0,raw_sum.length-19);
                                    if(plotData){
                                        for(int i=0; i<copy_sum.length;i++){
                                            addEntry((float)copy_sum[i],0);
                                        }

                                    }

                                    smooth_sum = lineTask_analyze.smoothSum(copy_sum);
                                    cnt_task=lineTask_analyze.countTurnHand(smooth_sum);
//                                    Log.d("sdsdd ", String.valueOf(copy_sum.length));

                                    ref.child("task").child("time").setValue(task_time/1000);
                                    ref.child("task").child("sum").setValue(mConsumerService.d_sum);
//                                ref.child("task").child("rawsum").setValue(ArrayToArraylist(raw_sum));
                                    ref.child("task").child("smoothsum").setValue(ArrayToArraylist(smooth_sum));
                                    ref.child("task").child("cnttask").setValue(ArrayToArraylist(cnt_task));

                                    plotData = false;
                                }


                                @Override
                                public void onCancelled(@NonNull DatabaseError databaseError) {

                                }
                            });
                        }

                    }

                }
                break;
            }
            default:
        }
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mConsumerService = ((ConsumerService.LocalBinder) service).getService();
            updateTextView("onServiceConnected");
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mConsumerService = null;
            mIsBound = false;
            updateTextView("onServiceDisconnected");
        }
    };

    public static void addMessage(String data) {
        mMessageAdapter.addMessage(new Message(data));
    }

    public static void updateTextView(final String str) {
        mTextView.setText(str);
    }

    private class MessageAdapter extends BaseAdapter {
        private static final int MAX_MESSAGES_TO_DISPLAY = 20;
        private List<Message> mMessages;

        public MessageAdapter() {
            mMessages = Collections.synchronizedList(new ArrayList<Message>());
        }

        void addMessage(final Message msg) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mMessages.size() == MAX_MESSAGES_TO_DISPLAY) {
                        mMessages.remove(0);
                        mMessages.add(msg);
                    } else {
                        mMessages.add(msg);
                    }
                    notifyDataSetChanged();
                    mMessageListView.setSelection(getCount() - 1);
                }
            });
        }

        void clear() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mMessages.clear();
                    notifyDataSetChanged();
                }
            });
        }

        @Override
        public int getCount() {
            return mMessages.size();
        }

        @Override
        public Object getItem(int position) {
            return mMessages.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflator = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View messageRecordView = null;
            if (inflator != null) {
                messageRecordView = inflator.inflate(R.layout.message, null);
                TextView tvData = (TextView) messageRecordView.findViewById(R.id.tvData);
                Message message = (Message) getItem(position);
                tvData.setText(message.data);
            }
            return messageRecordView;
        }
    }

    private static final class Message {
        String data;

        public Message(String data) {
            super();
            this.data = data;
        }
    }

    //-------------------------------------내가 만든 함수------------------------

    private void clear(){
        mConsumerService.d_x.clear();
        mConsumerService.d_y.clear();
        mConsumerService.d_z.clear();
        mConsumerService.d_time.clear();
        mConsumerService.d_sum.clear();
        mConsumerService.ii=0;
    }
    //어레이리스트에서 어레이로 바꿔주기 (밴드패스 필터링 라이브러리 때문에)
    public double[] ArratlistToArray(ArrayList<Float> arr) {
        double[] return_value=new double[arr.size()];

        for (int i =0;i<arr.size();i++){
            return_value[i]= arr.get(i);
        }

        return return_value;
    }
//
    public ArrayList<Float> ArrayToArraylist(double[] arr){
        ArrayList<Float> return_val= new ArrayList<Float>();

        for (int i =0;i<arr.length;i++){
            return_val.add((float)arr[i]);
        }
        return return_val;
    }

    private void startPlot(){

        if(graphThread != null){
            graphThread.interrupt();
        }

        graphThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while(true){
                    plotData = true;
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e){
                        e.printStackTrace();
                    }
                }
            }
        });

        graphThread.start();
    }

    // LineChart addEntry()
    private static void addEntry(float idx, int order){
        LineData data = mChart.getData();

        if (data != null){
            ILineDataSet set = data.getDataSetByIndex(order);

            if(set == null){
                set = createSet(order);
                data.addDataSet(set);
            }

            data.addEntry(new Entry(set.getEntryCount(), idx), order);
            data.setDrawValues(false);
            data.notifyDataChanged();
        }

        // let the chart know it's data has changed
        mChart.notifyDataSetChanged();

        mChart.setMaxVisibleValueCount(220);
        mChart.moveViewToX(data.getEntryCount());
    }

    private final static int[] colors = new int[] {
            ColorTemplate.VORDIPLOM_COLORS[0],
            ColorTemplate.VORDIPLOM_COLORS[1],
            ColorTemplate.VORDIPLOM_COLORS[4]
    };

    // LineChart createSet()
    private static LineDataSet createSet(int order){
        // order 필요 없어짐 - 수정해야 함
        LineDataSet set;
        set = new LineDataSet(null, "SVM Variation");
        set.setColor(colors[1]);
        set.setLineWidth(2.5f);
        set.setDrawCircles(false);
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setMode(LineDataSet.Mode.LINEAR);
//      set.setCubicIntensity(0.2f);
        return set;
    }


//
//
//    public float[] Mean_xyz(float[] x,float[] y , float[] z){
//        mean_xyz=new float[x.length];
//        for(int i=0;i<x.length;i++){
//            mean_xyz[i]=(x[i]+y[i]+z[i])/3;
//        }
//        return mean_xyz;
//    }
//
//    // Function that Calculate Root
//    // Mean Square
//    static float rmsValue(float[] arr, int n)
//    {
//        int square = 0;
//        float mean = 0;
//        float root = 0;
//
//        // Calculate square.
//        for(int i = 0; i < n; i++)
//        {
//            square += Math.pow(arr[i], 2);
//        }
//
//        // Calculate Mean.
//        mean = (square / (float) (n));
//
//        // Calculate Root.
//        root = (float)Math.sqrt(mean);
//
//        return root;
//    }

//    public float TremorMagnitude(float[] mean){
//        float val;
//        float[] hil_output=new float[mean.length];
//        float[] abs_hil=new float[hil_output.length];
//        Hilbert.apply(mean.length,mean,hil_output);
//        for(int i =0; i<hil_output.length;i++){
//            abs_hil[i]=Math.abs(hil_output[i]);
//        }
//
//        val=rmsValue(abs_hil,hil_output.length);
//
//        return val;
//
//    }

//    public float[] TremorMagnitude(float[] mean){
//        float val;
//        float[] hil_output=new float[mean.length];
//        float[] abs_hil=new float[hil_output.length];
//        Hilbert.apply(mean.length,mean,hil_output);
//        for(int i =0; i<hil_output.length;i++){
//            abs_hil[i]=Math.abs(hil_output[i]);
//        }
//
//        //val=rmsValue(abs_hil,hil_output.length);
//
//        return abs_hil;
//
//    }
//
//
//    public float FFT_freq(float[] band){
//        float max;
//        Fft fft = new Fft(band.length); // nx = number of samples of f(x)
//        Sampling sk = fft.getFrequencySampling1();
//        int nk = sk.getCount(); // number of frequencies sampled
//        float[] f = band; // nx real samples of input f(x)
//        float[] g = fft.applyForward(f); // nk complex samples of g(k)
//        for (int kk=0,kr=0,ki=kr+1; kk<nk; ++kk,kr+=2,ki+=2) {
//            double k = sk.getValue(kk); // frequency k in cycles/sample
//            // modify g[kr], the real part of g(k)
//            // modify g[ki], the imag part of g(k)
//        }
//
//        float[] h = fft.applyInverse(g); // nx real samples of output h(x)
//        for(int i=0;i<h.length;i++){
//            Log.d("1234", String.valueOf(h[i]));
//        }
//        max=h[0];
//
//        for(int i=1;i<h.length;i++){
//            if(max<h[i]){
//                max=h[i];
//                Log.d("12345", String.valueOf(i));
//            }
//        }
//        return max;
//    }

}
