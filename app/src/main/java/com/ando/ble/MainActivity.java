package com.ando.ble;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.telephony.mbms.MbmsErrors;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * 主页
 * <p>
 * Created by yangle on 2018/7/5.
 * Website：http://www.yangle.tech
 */

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private final int REQUEST_ENABLE_BT = 1;
    private RecyclerView rvDeviceList;
    private Button btnScan;
    private BluetoothAdapter mBtAdapter;
    private BluetoothLeScanner mBtScanner;
    private BleService mBleService;
    private BroadcastReceiver mBleReceiver;
    private DeviceListAdapter mDeviceListAdapter;
    private List<BluetoothDevice> mBluetoothDeviceList;
    private List<String> mRssiList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rvDeviceList = findViewById(R.id.rv_device_list);
        btnScan = findViewById(R.id.btn_scan);
        btnScan.setOnClickListener(this);
        initBle();
        initData();
        registerBleReceiver();
    }

    /**
     * 初始化蓝牙
     */
    private void initBle() {
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        mBtScanner = mBtAdapter.getBluetoothLeScanner();
        if (mBtAdapter == null) {
            Toast.makeText(this, "蓝牙不可用", Toast.LENGTH_LONG).show();
            return;
        }

        if (!mBtAdapter.isEnabled()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQUEST_ENABLE_BT);
            return;
        }

        // 搜索蓝牙设备
        scanBleDevice();
    }

    /**
     * 初始化数据
     */
    private void initData() {
        // 蓝牙设备列表
        mBluetoothDeviceList = new ArrayList<>();
        // 蓝牙设备RSSI列表
        mRssiList = new ArrayList<>();
        mDeviceListAdapter = new DeviceListAdapter(mBluetoothDeviceList, mRssiList);
        rvDeviceList.setLayoutManager(new LinearLayoutManager(this));
        rvDeviceList.setAdapter(mDeviceListAdapter);

        // 连接蓝牙设备
        mDeviceListAdapter.setOnItemClickListener(new DeviceListAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                Toast.makeText(MainActivity.this, "开始连接", Toast.LENGTH_SHORT).show();
                mBtScanner.stopScan(mScanCallback);
                mBleService.connect(mBtAdapter, mBluetoothDeviceList.get(position).getAddress());
            }
        });
    }

    /**
     * 注册蓝牙信息接收器
     */
    private void registerBleReceiver() {
        // 绑定服务
        Intent intent = new Intent(this, BleService.class);
        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
        startService(intent);

        // 注册蓝牙信息广播接收器
        IntentFilter filter = new IntentFilter();
        filter.addAction(BleService.ACTION_GATT_CONNECTED);
        filter.addAction(BleService.ACTION_GATT_DISCONNECTED);
        filter.addAction(BleService.ACTION_GATT_SERVICES_DISCOVERED);
        filter.addAction(BleService.ACTION_DATA_AVAILABLE);
        filter.addAction(BleService.ACTION_CONNECTING_FAIL);
        mBleReceiver = new BleReceiver();
        registerReceiver(mBleReceiver, filter);
    }

    /**
     * 搜索蓝牙设备
     */
    private void scanBleDevice() {
        mBtScanner.stopScan(mScanCallback);
        mBtScanner.startScan(mScanCallback);
        // 搜索10s
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                mBtScanner.stopScan(mScanCallback);
            }
        }, 10000);
    }

    /**
     * 搜索蓝牙设备回调
     */
    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            final BluetoothDevice bluetoothDevice = result.getDevice();

            if (!mBluetoothDeviceList.contains(bluetoothDevice)) {
                Log.e("123", "设备:" + bluetoothDevice.getName() + "    " + result.getScanRecord().getDeviceName());
                mBluetoothDeviceList.add(bluetoothDevice);
                mRssiList.add(String.valueOf(result.getRssi()));
                mDeviceListAdapter.notifyDataSetChanged();
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };

    /**
     * 服务
     */
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mBleService = ((BleService.LocalBinder) rawBinder).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName classname) {
            mBleService = null;
        }
    };

    /**
     * 蓝牙信息接收器
     */
    private class BleReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TextUtils.isEmpty(action)) {
                return;
            }
            switch (action) {
                case BleService.ACTION_GATT_CONNECTED:
                    Toast.makeText(MainActivity.this, "蓝牙已连接", Toast.LENGTH_SHORT).show();
                    break;
                case BleService.ACTION_GATT_DISCONNECTED:
                    Toast.makeText(MainActivity.this, "蓝牙已断开", Toast.LENGTH_SHORT).show();
                    mBleService.release();
                    break;
                case BleService.ACTION_CONNECTING_FAIL:
                    Toast.makeText(MainActivity.this, "蓝牙已断开", Toast.LENGTH_SHORT).show();
                    mBleService.disconnect();
                    break;
                case BleService.ACTION_DATA_AVAILABLE:
                    byte[] data = intent.getByteArrayExtra(BleService.EXTRA_DATA);
                    Log.i("蓝牙", "收到的数据：" + ByteUtils.byteArrayToHexString(data));
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.btn_scan) { // 搜索蓝牙
            // 搜索蓝牙设备
            scanBleDevice();
            // 初始化数据
            initData();
            // 注册蓝牙信息接收器
            registerBleReceiver();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) {
            return;
        }
        if (requestCode == REQUEST_ENABLE_BT) {// 搜索蓝牙设备
            scanBleDevice();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBleReceiver != null) {
            unregisterReceiver(mBleReceiver);
            mBleReceiver = null;
        }
        unbindService(mServiceConnection);
        mBleService = null;
    }
}
