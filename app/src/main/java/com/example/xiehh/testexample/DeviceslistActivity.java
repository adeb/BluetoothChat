package com.example.xiehh.testexample;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Set;


public class DeviceslistActivity extends Activity {
    private static final String TAG = "DeviceListActivity";
    private static final boolean D = true;

    //Return intent extra
    public static String EXTRA_DEVICE_ADDRESS = "device_address";

    private static final int REQUEST_FOR_BT = 2;

    private boolean mDiscoveryFlag = true;

    Button scanButton;

    //member fields
    private BluetoothAdapter mBtAdapter;
    private ArrayAdapter<String> mPairedDevicesArrayAdapter;
    private ArrayAdapter<String> mNewDevicesArrayAdapter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.device_list);

        setResult(Activity.RESULT_CANCELED);

        scanButton=(Button)findViewById(R.id.id_button_scan);
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mDiscoveryFlag == true) {
                    doDiscovery();
                    scanButton.setText("Cancel");
                    mDiscoveryFlag = false;
                }else{
                    stopDiscovery();
                    scanButton.setText("Scan");
                    mDiscoveryFlag = true;

                    setProgressBarIndeterminateVisibility(false);
                    setTitle("Scanning cancel");
                }
                //v.setVisibility(View.GONE);
            }
        });

        // Initialize array adapters. One for already paired devices and
        // one for newly discovered devices
        mPairedDevicesArrayAdapter = new ArrayAdapter<String>(this,R.layout.device_name);
        mNewDevicesArrayAdapter = new ArrayAdapter<String>(this,R.layout.device_name);

        // Find and set up the ListView for paired devices
        ListView pairedListView = (ListView)findViewById(R.id.id_paired_devices);
        pairedListView.setAdapter(mPairedDevicesArrayAdapter);
        pairedListView.setOnItemClickListener(mDeviceClickListener);

        // Find and set up the ListView for newly discovered devices
        ListView newDevicesListView = (ListView)findViewById(R.id.id_new_devices);
        newDevicesListView.setAdapter(mNewDevicesArrayAdapter);
        newDevicesListView.setOnItemClickListener(mDeviceClickListener);

        //Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(mReceiver, filter);

        //Register for broadcasts when discovery has finished
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(mReceiver,filter);

        //注册一个蓝牙状态改变的广播
        filter = new IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        this.registerReceiver(mReceiver,filter);

        //get the local bluetooth adapter
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();

        //get a set of currently paired devices
        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();

        // If there are paired devices, add each one to the ArrayAdapter
        if(pairedDevices.size()>0){
            findViewById(R.id.id_title_paired_devices).setVisibility(View.VISIBLE);
            for(BluetoothDevice device:pairedDevices){
                mPairedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        }else{
            String noDevices = getResources().getText(R.string.none_paired).toString();
            mPairedDevicesArrayAdapter.add(noDevices);
        }
    }

    private void doDiscovery()
    {
        if(D) Log.d(TAG, "doDiscovery()");

        if(!mBtAdapter.isEnabled()){
            Log.d(TAG,"Bt is not enable request BT enable");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent,REQUEST_FOR_BT);
        }else {

            //Indicate scanning in the title
            setProgressBarIndeterminateVisibility(true);
            setTitle(R.string.scanning);

            //Turn on sub-title for new devices
            findViewById(R.id.id_title_new_devices).setVisibility(View.VISIBLE);

            //If we're already discovery,stop it
            if (mBtAdapter.isDiscovering()) {
                mBtAdapter.cancelDiscovery();
            }

            //Request discovery from BluetoothAdapter
            mBtAdapter.startDiscovery();
        }
    }

    private void stopDiscovery(){
        if(mBtAdapter.isDiscovering()){
            mBtAdapter.cancelDiscovery();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        //Make sure we're not doing discovery anymore
        if(mBtAdapter != null){
            mBtAdapter.cancelDiscovery();
        }

        //Unregister broadcast listeners
        this.unregisterReceiver(mReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
/*
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
*/
    //The on-click listener for all devices in the ListView
    private AdapterView.OnItemClickListener  mDeviceClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            //Cancel discovery because it's costly and we're about to connect
            mBtAdapter.cancelDiscovery();

            Log.d(TAG, "---------stop discovery---------");

            //Get the device MAC address, which is the last 17 chars in the view
            String info = ((TextView) view).getText().toString();
            String address = info.substring(info.length() - 17);

            //Create the result Intent and include the MAC address
            Intent intent = new Intent();
            intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

            //set result and finish this Activity
            setResult(Activity.RESULT_OK, intent);
            finish();
        }
    };

    //The broadcastReceiver that listens for discovered devices and changes the title when discovery is finished
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            //when discovery finds a device
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                //get the bluetoothdevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                //if it's already paired , skip it, because it's been listed already
                if(device.getBondState() != BluetoothDevice.BOND_BONDED){
                    mNewDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());

                    //when discovery is finished , change the activity title
                }else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
                    setProgressBarIndeterminateVisibility(false);
                    setTitle(R.string.select_device);
                    if(mNewDevicesArrayAdapter.getCount() == 0){
                        String noDevices = getResources().getText(R.string.none_found).toString();
                        mNewDevicesArrayAdapter.add(noDevices);
                    }
                }
            }

            if(BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)){
                Log.e(TAG, "------------------>Bluetooth state has change<--------------");
            }
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch(requestCode){
            case REQUEST_FOR_BT:
                if(resultCode == Activity.RESULT_OK){

                    //蓝牙打开后先列出已配对的蓝牙设备
                    Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();
                    // If there are paired devices, add each one to the ArrayAdapter
                    if(pairedDevices.size()>0){
                        findViewById(R.id.id_title_paired_devices).setVisibility(View.VISIBLE);
                        for(BluetoothDevice device:pairedDevices){
                            mPairedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                        }
                    }

                    //开始搜索
                    Log.e(TAG,"Request BT enable is success");
                    //Indicate scanning in the title
                    setProgressBarIndeterminateVisibility(true);
                    setTitle(R.string.scanning);

                    //Turn on sub-title for new devices
                    findViewById(R.id.id_title_new_devices).setVisibility(View.VISIBLE);

                    //If we're already discovery,stop it
                    if(mBtAdapter.isDiscovering()){
                        mBtAdapter.cancelDiscovery();
                    }

                    //Request discovery from BluetoothAdapter
                    mBtAdapter.startDiscovery();
                }else{
                    //user did not enable Bluetooth or an error occured
                    Log.e(TAG,"BT not enbale");
                    Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
        }
        //super.onActivityResult(requestCode, resultCode, data);
    }
}
