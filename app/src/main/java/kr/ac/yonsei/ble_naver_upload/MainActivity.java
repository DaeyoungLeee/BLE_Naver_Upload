 package kr.ac.yonsei.ble_naver_upload;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.primitives.Ints;
import com.vise.baseble.ViseBle;
import com.vise.baseble.callback.IBleCallback;
import com.vise.baseble.callback.IConnectCallback;
import com.vise.baseble.callback.scan.IScanCallback;
import com.vise.baseble.callback.scan.ScanCallback;
import com.vise.baseble.common.PropertyType;
import com.vise.baseble.core.BluetoothGattChannel;
import com.vise.baseble.core.DeviceMirror;
import com.vise.baseble.exception.BleException;
import com.vise.baseble.model.BluetoothLeDevice;
import com.vise.baseble.model.BluetoothLeDeviceStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    Button startScan, stopScan, btn_send_data;
    TextView txt_value_noti, txt_value_write;
    ListView listView;
    String dataValue;

    byte[] bytes = {0x5A, 0x01, (byte) 0xB1, 0x71, (byte) 0xA5};

    private static final UUID ServiceUUID = UUID.fromString("0000FFF0-0000-1000-8000-00805F9B34FB");
    private static final UUID CharacteristicUUID_Noti = UUID.fromString("0000FFF1-0000-1000-8000-00805F9B34FB");
    private static final UUID CharacteristicUUID_Write = UUID.fromString("0000FFF2-0000-1000-8000-00805F9B34FB");
    private static final UUID DescriptorUUID = UUID.fromString("00002904-0000-1000-8000-00805F9B34FB");

    public static final String TAG = "BLE1_ACTIVITY";

    final ViseBle viseBleSet = ViseBle.getInstance();
    BluetoothGattChannel bluetoothGattChannel_Noti;
    BluetoothGattChannel bluetoothGattChannel_Write;
    BluetoothGattChannel bluetoothGattChannel_read;

    DeviceMirror deviceMirrorCustom;

    final ArrayList<String> arrayList_information = new ArrayList<>();
    final ArrayList<String> arrayList_address = new ArrayList<>();
    final ArrayList<String> arrayList_name = new ArrayList<>();
    final ArrayList<Integer> arrayList_rssi = new ArrayList<>();
    final ArrayList<ParcelUuid[]> arrayList_uuid = new ArrayList<>();
    final ArrayList<BluetoothLeDevice> arrayList_device = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startScan = findViewById(R.id.btn_startScan_ble);
        stopScan = findViewById(R.id.btn_stopScan_ble);
        listView = findViewById(R.id.listView_git);
        txt_value_noti = findViewById(R.id.txt_value_noti);
        txt_value_write = findViewById(R.id.txt_value_write);
        btn_send_data = findViewById(R.id.btn_send_data);

        ViseBle.config()
                .setScanTimeout(-1)
                .setConnectTimeout(10 * 1000) // 연결 시간 초과 시간설정
                .setOperateTimeout(5 * 1000)  // 데이터 작업 시간 초과 설정
                .setConnectRetryCount(3)      // 연결 실패 재시도 횟수 설정
                .setConnectRetryInterval(1000)// 재시도 시간 간격 설정
                .setOperateRetryCount(3)      // 데이터 조작 실패한 재시도 설정
                .setOperateRetryInterval(1000)// 데이터 조작 실패에 대한 재시도 시간간격
                .setMaxConnectCount(3);       // 연결된 최대 장치 수 설정
        ViseBle.getInstance().init(this);

        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION}, 100);

        startScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 검색(스캔)
                viseBleSet.startScan(scanCallback);
            }
        });

        stopScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 스캔 중지
                viseBleSet.stopScan(scanCallback);
            }
        });

        // send data (Request)
        btn_send_data.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bluetoothGattChannel_Write = new BluetoothGattChannel.Builder()
                        .setBluetoothGatt(deviceMirrorCustom.getBluetoothGatt())
                        .setPropertyType(PropertyType.PROPERTY_WRITE)
                        .setServiceUUID(ServiceUUID)
                        .setCharacteristicUUID(CharacteristicUUID_Write)
                        .setDescriptorUUID(null)
                        .builder();
                //deviceMirror.registerNotify(false);
                deviceMirrorCustom.bindChannel(iBleCallback_write, bluetoothGattChannel_Write);
                deviceMirrorCustom.writeData(bytes);

                // read data
/*
                bluetoothGattChannel_read = new BluetoothGattChannel.Builder()
                        .setBluetoothGatt(deviceMirrorCustom.getBluetoothGatt())
                        .setPropertyType(PropertyType.PROPERTY_READ)
                        .setServiceUUID(ServiceUUID)
                        .setCharacteristicUUID(CharacteristicUUID_Noti)
                        .setDescriptorUUID(null)
                        .builder();
                deviceMirrorCustom.bindChannel(iBleCallback_write, bluetoothGattChannel_Write);
                deviceMirrorCustom.readData();*/
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                viseBleSet.connectByMac(arrayList_address.get(position), iConnectCallback);

            }
        });
    }

    final IConnectCallback iConnectCallback = new IConnectCallback() {
        @Override
        public void onConnectSuccess(final DeviceMirror deviceMirror) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "연결되었습니다.", Toast.LENGTH_SHORT).show();

                            bluetoothGattChannel_Noti = new BluetoothGattChannel.Builder()
                                    .setBluetoothGatt(deviceMirror.getBluetoothGatt())
                                    .setPropertyType(PropertyType.PROPERTY_NOTIFY)
                                    .setServiceUUID(ServiceUUID)
                                    .setCharacteristicUUID(CharacteristicUUID_Noti)
                                    .setDescriptorUUID(null)
                                    .builder();
                            deviceMirror.bindChannel(iBleCallback_noti, bluetoothGattChannel_Noti);
                            deviceMirror.registerNotify(false);
                            deviceMirror.setNotifyListener(bluetoothGattChannel_Noti.getGattInfoKey(), iBleCallback_noti);

                            deviceMirrorCustom = deviceMirror;

                            /*bluetoothGattChannel_Write = new BluetoothGattChannel.Builder()
                                    .setBluetoothGatt(deviceMirror.getBluetoothGatt())
                                    .setPropertyType(PropertyType.PROPERTY_WRITE)
                                    .setServiceUUID(ServiceUUID)
                                    .setCharacteristicUUID(CharacteristicUUID_Write)
                                    .setDescriptorUUID(null)
                                    .builder();
                            //deviceMirror.registerNotify(false);
                            deviceMirror.bindChannel(iBleCallback_write, bluetoothGattChannel_Write);

                            deviceMirror.writeData(bytes);*/

                            /*
                            bluetoothGattChannel_read = new BluetoothGattChannel.Builder()
                                    .setBluetoothGatt(deviceMirror.getBluetoothGatt())
                                    .setPropertyType(PropertyType.PROPERTY_READ)
                                    .setServiceUUID(ServiceUUID)
                                    .setCharacteristicUUID(CharacteristicUUID)
                                    .setDescriptorUUID(null)
                                    .builder();
                            deviceMirror.bindChannel(iBleCallback, bluetoothGattChannel_read);
                            deviceMirror.readData();*/

                        }
                    });
                }
            }).start();
        }

        @Override
        public void onConnectFailure(BleException exception) {

        }

        @Override
        public void onDisconnect(boolean isActive) {

        }
    };

    IBleCallback iBleCallback_write = new IBleCallback() {
        @Override
        public void onSuccess(final byte[] data, final BluetoothGattChannel bluetoothGattChannel, BluetoothLeDevice bluetoothLeDevice) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            txt_value_write.setText(Arrays.toString(data));
                            Toast.makeText(MainActivity.this, "write", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }).start();
        }

        @Override
        public void onFailure(BleException exception) {

        }
    };

    IBleCallback iBleCallback_noti = new IBleCallback() {
        @Override
        public void onSuccess(final byte[] data, BluetoothGattChannel bluetoothGattChannel, BluetoothLeDevice bluetoothLeDevice) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            txt_value_noti.setText(Arrays.toString(data));
                            Toast.makeText(MainActivity.this, "noti", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }).start();
        }

        @Override
        public void onFailure(BleException exception) {

        }
    };

    // 스캔 콜백함수
    IScanCallback iScanCallback = new IScanCallback() {
        @Override
        public void onDeviceFound(BluetoothLeDevice bluetoothLeDevice) {
            arrayList_address.add(bluetoothLeDevice.getAddress());
            arrayList_name.add(bluetoothLeDevice.getName());
            arrayList_rssi.add(bluetoothLeDevice.getRssi());
            arrayList_device.add(bluetoothLeDevice);
            arrayList_uuid.add(bluetoothLeDevice.getDevice().getUuids());

            arrayList_information.add(("Device = " + bluetoothLeDevice.getName()
                    + "  UUID = " + bluetoothLeDevice.getDevice().getUuids()
                    + "Address = " + bluetoothLeDevice.getAddress()));

            ArrayAdapter arrayAdapter = new ArrayAdapter(getApplicationContext(),
                    R.layout.support_simple_spinner_dropdown_item,
                    arrayList_information);
            listView.setAdapter(arrayAdapter);

        }

        @Override
        public void onScanFinish(BluetoothLeDeviceStore bluetoothLeDeviceStore) {

        }

        @Override
        public void onScanTimeout() {

        }
    };
    ScanCallback scanCallback = new ScanCallback(iScanCallback);


    @Override
    protected void onDestroy() {
        super.onDestroy();
        ViseBle.getInstance().disconnect();
    }

}
class UnsignedByte {

    public static short parse( byte[] data, int offset ) {
        return (short)(data[offset] & 0xff);
    }

    public static short parse( byte[] data ) {
        return parse( data, 0 );
    }

    public static byte[] parse( short number ) {
        return new byte[] { (byte)(number & 0xff) };
    }

}
