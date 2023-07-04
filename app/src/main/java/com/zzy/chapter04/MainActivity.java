package com.zzy.chapter04;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.util.Log;
import android.util.SparseArray;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.squareup.haha.perflib.ArrayInstance;
import com.squareup.haha.perflib.ClassInstance;
import com.squareup.haha.perflib.ClassObj;
import com.squareup.haha.perflib.Heap;
import com.squareup.haha.perflib.HprofParser;
import com.squareup.haha.perflib.Instance;
import com.squareup.haha.perflib.Snapshot;
import com.squareup.haha.perflib.io.HprofBuffer;
import com.squareup.haha.perflib.io.MemoryMappedFileBuffer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    SparseArray<List<Instance>> sparseArray = new SparseArray<>();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.duplicate);
        Bitmap duplicatBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.duplicate);
        Bitmap newBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.origin);
        ImageView imageView = findViewById(R.id.one_img);
        imageView.setImageBitmap(bitmap);
        ((ImageView)findViewById(R.id.two_img)).setImageBitmap(duplicatBitmap);
        if (bitmap == duplicatBitmap) {
            Log.d(TAG, "Bitmap duplicate");
        } else {
            Log.d(TAG, "Bitmap not duplicate");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            String fileName = getDataDir() + File.separator + System.currentTimeMillis() + ".hprof";
            Log.d(TAG, "fileName: " + fileName);
            File file = new File(fileName);
            Log.d(TAG, "before dump file exists: " + file.exists());
            try {
                Debug.dumpHprofData(fileName);
            } catch (IOException e) {
                Log.e(TAG, "dumpHprofData error!!!!!!!!!!!!!!!");
            }
            Log.d(TAG, "after dump file exists: " + file.exists());
            HprofBuffer hprofBuffer;
            try {
                hprofBuffer = new MemoryMappedFileBuffer(file);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            HprofParser parser = new HprofParser(hprofBuffer);
            final Snapshot snapshot = parser.parse();
            snapshot.computeDominators();
            // 获得Bitmap Class
            final ClassObj bitmapClass = snapshot.findClass("android.graphics.Bitmap");
            // 获得heap, 只需要分析app和default heap即可
            Heap heap = snapshot.getHeap("app");
            // 从heap中获得所有的Bitmap实例
            final List<Instance> bitmapInstances = bitmapClass.getHeapInstances(heap.getId());

            // 从Bitmap实例中获得buffer数组
            for(Instance instance : bitmapInstances) {
                ArrayInstance buffer = HahaHelper.fieldValue(((ClassInstance) instance).getValues(), "mBuffer");
                int hashCode = Arrays.hashCode(buffer.getValues());
                Log.d(TAG, "hashCode: " + hashCode);
                if (sparseArray.indexOfKey(hashCode) >= 0) {
                    sparseArray.get(hashCode).add(instance);
                } else {
                    List<Instance> list = new ArrayList<>();
                    list.add(instance);
                    sparseArray.put(hashCode, list);
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        printMethod();
    }

    private void printMethod() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0, size = sparseArray.size(); i < size; i++) {
            List<Instance> instances = sparseArray.valueAt(i);
            int listSize = instances.size();
            if (listSize > 1) {
                sb.append("\"duplcateCount\":")
                        .append(instances.size())
                        .append("\n")
                        .append("\"stacks\": \n");
                for (int j = 0; j < listSize; j++) {
                    sb.append("===================================================== \n");
                    sb.append(getTraceString(getTraceFromInstance(instances.get(j))));
                    sb.append("===================================================== \n");
                }

                sb.append("\"bufferHash\":").append("\"").append(sparseArray.keyAt(i)).append("\"\n");
                int width = HahaHelper.fieldValue(((ClassInstance) instances.get(0)).getValues(), "mWidth");
                int height = HahaHelper.fieldValue(((ClassInstance) instances.get(0)).getValues(), "mHeight");
                sb.append("\"width\":" + width + "\n")
                    .append("\"height\":" + height + "\n")
                    .append("\"bufferSize\":" + instances.get(0).getSize() + "\n")
                    .append("----------------------------------------------------- \n");
            }
        }
        Log.d(TAG, "result:" + sb);
    }

    public static ArrayList<Instance> getTraceFromInstance(Instance instance) {
        ArrayList<Instance> arrayList = new ArrayList<>();
        while(instance != null && instance.getDistanceToGcRoot() != 0 &&
                instance.getDistanceToGcRoot() != Integer.MAX_VALUE) {
            arrayList.add(instance);
            instance = instance.getNextInstanceToGcRoot();
        }
        return arrayList;
    }

    public static String getTraceString(List<Instance> instances) {
        StringBuilder sb = new StringBuilder();
        if (instances.size() > 0) {
            for (Instance instance : instances) {
                sb.append(instance.getClassObj().getClassName());
                sb.append("\n");
            }
        }
        return sb.toString();
    }

}