package com.example.xiehh.testexample;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Created by xiehh on 2015/3/6.
 */
public class BluetoothChat extends Activity {
    //Debugging
    private static final String TAG = "BluetoothChat";
    private static final boolean D = true;
    private boolean blStop = false;

    //Message types sent from the BluetoothChatServerice Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    //Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    //Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int REQUEST_DISCOVERY_BT = 3;

    //Layout views
    //private TextView mTitle;
    private TextView mInfoTitle;
    private ListView mConversationView;
    private EditText mOutEditText;
    private Button mSendButton;
    private Button mDisconnectBt;

    //定义通知栏NotificationManager
    private static final String NS = Context.NOTIFICATION_SERVICE;
    private NotificationManager mNotificationManager = null;
    Notification mNotification = null;
    private int NotificationID = 1928178;
    long when = System.currentTimeMillis();
    String contentTitle = "蓝牙聊天消息";
    CharSequence contentText = "";

    //Name of the connected device
    private String mConnectedDeviceName = null;
    //Array adapter for the conversation thread
    private ArrayAdapter<String> mConversationArrayAdapter;
    //String buffer for outgoning messages
    private StringBuffer mOutStringBuffer;
    //Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    //Member object for the chat services
    private BluetoothChatService mChatService = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(D) Log.e(TAG, "+++ ON CREATE +++");

        mNotificationManager = (NotificationManager) getSystemService(NS);


        //set up the window layout
        //requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        setContentView(R.layout.main);
        //getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_title);

        //set up the custom title
        //mTitle = (TextView) findViewById(R.id.id_title_left_text);
        //mTitle.setText(R.string.app_name);
       //mTitle = (TextView) findViewById(R.id.id_title_right_text);

        mInfoTitle = (TextView) findViewById(R.id.id_msg_title);
        mInfoTitle.setText("No devices");

        mDisconnectBt = (Button) findViewById(R.id.id_disconnect);
        mDisconnectBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mChatService != null){
                    Log.e(TAG, "Stop Bluetooth chat...");
                    mChatService.stop();
                    //开启监听
                    Log.e(TAG,"Start to listen...1");
                    mChatService.start();
                }
            }
        });


        //Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        //if the adapter is null , then Bluetooth is not supported
        if(mBluetoothAdapter == null){
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        if(D) Log.e(TAG, "+++on Start+++");

        //if BT is not on,request that it be enabled.
        //setupChat() will then be called during onActivityResult
        if(!mBluetoothAdapter.isEnabled()){
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent,REQUEST_ENABLE_BT);
            //otherview setup the chat session
        }else{
            if(mChatService == null) setupChat();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(D) Log.e(TAG, "+++ on Resume+++");

        //Performing this check in onResume() covers the case in which BT was
        //not enable during onStart() so we were paused to enable it...
        //onResume() will be called when ACTION_REQUEST_ENABLE activity returns
        if(mChatService != null){
            //only if the state is STATE_NONE, DO we know that we haven't started already
            if(mChatService.getState() == BluetoothChatService.STATE_NONE){
                //start the bluetooth chat services
                Log.e(TAG, "Start Bluetooth chat...");
                mChatService.start();
                blStop = true;
            }
        }

//        if(blStop == true){
//            Log.e(TAG, "Start Bluetooth chat again...");
//            mChatService.start();
//        }
    }

    private void setupChat(){
        Log.d(TAG, "setupChat()");

        //Initalize the array adapter for the conversation thread
        mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
        mConversationView = (ListView)findViewById(R.id.id_in);
        mConversationView.setAdapter(mConversationArrayAdapter);

        // Initialize the compose field with a listener for the return key
        mOutEditText = (EditText) findViewById(R.id.id_edit_text_out);
        mOutEditText.setOnEditorActionListener(mWriteListener);

        // Initialize the send button with a listener that for click events
        mSendButton = (Button) findViewById(R.id.id_button_send);
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                TextView view = (TextView) findViewById(R.id.id_edit_text_out);
                String message = view.getText().toString();
                sendMessage(message);
            }
        });

        // Initialize the BluetoothChatService to perform bluetooth connection
        mChatService = new BluetoothChatService(this, mHandler);

        //Initialize the buffer for outgoing message
        mOutStringBuffer = new StringBuffer("");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "- on Pause -");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "- on Stop -");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        //Stop the Bluetooth chat services
        if(mChatService != null) mChatService.stop();

        if(D) Log.e(TAG, "---On Destroy--");
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        PackageManager pm = getPackageManager();
        ResolveInfo homeInfo = pm.resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0);
        if(keyCode == KeyEvent.KEYCODE_BACK){
            Log.e(TAG,"Back to running....");
            ActivityInfo ai = homeInfo.activityInfo;
            Intent startIntent = new Intent(Intent.ACTION_MAIN);
            startIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            startIntent.setComponent(new ComponentName(ai.packageName, ai.name));
            startActivitySafely(startIntent);
            return true;
        }else {
            return super.onKeyDown(keyCode, event);
        }
    }

    private void startActivitySafely(Intent intent){
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try{
            startActivity(intent);
        }catch (ActivityNotFoundException e){
            Toast.makeText(this, "null", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "startActivitySafely error:", e);
        }catch (SecurityException e1){
            Toast.makeText(this, "null", Toast.LENGTH_SHORT).show();
            Log.e(TAG,"startActivitySafely error2:", e1);
        }
    }

    private void ensureDiscoverable(){
        if(D) Log.d(TAG, "ensure discoverable");
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    private void sendMessage(String message){
        //Check that we're actually connected before trying anything
        if(mChatService.getState()!= BluetoothChatService.STATE_CONNECTED){
            Toast.makeText(this,R.string.not_connected,Toast.LENGTH_SHORT).show();
            return;
        }

        //check that there's actually something to send
        if(message.length()>0){
            //Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send);

            //Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
    }

    private TextView.OnEditorActionListener mWriteListener =
            new TextView.OnEditorActionListener(){
                public boolean onEditorAction(TextView view, int actionID, KeyEvent event){
                    //If the action is a key-up event on the return key, send the message
                    if(actionID == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP){
                        String message = view.getText().toString();
                        sendMessage(message);
                    }
                    if(D) Log.i(TAG, "End OnEditorAction");
                    return true;
                }
            };

    //The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
        public void handleMessage(Message msg){
            switch(msg.what){
                case MESSAGE_STATE_CHANGE:
                    if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE:"+ msg.arg1);
                    switch(msg.arg1){
                        case BluetoothChatService.STATE_CONNECTED:
                            //mTitle.setText(R.string.title_connected_to);
                            //mTitle.append(mConnectedDeviceName);
                            mInfoTitle.setText(R.string.title_connected_to);
                            mInfoTitle.append(mConnectedDeviceName);
                            mConversationArrayAdapter.clear();
                            break;
                        case BluetoothChatService.STATE_CONNECTING:
                            //mTitle.setText(R.string.title_connecting);
                            mInfoTitle.setText(R.string.title_connecting);
                            break;
                        case BluetoothChatService.STATE_LISTEN:
                        case BluetoothChatService.STATE_NONE:
                            //mTitle.setText(R.string.title_not_connected);
                            mInfoTitle.setText(R.string.title_not_connected);
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[])msg.obj;
                    //construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    mConversationArrayAdapter.add("Me: "+writeMessage);
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[])msg.obj;
                    //construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    mConversationArrayAdapter.add(mConnectedDeviceName+": "+readMessage);

                    //判断是否在前台动行，如果不是的话有消息来更新通知栏
                    if(isRunningForeground(getApplicationContext())==false) {
                        showNotification(R.drawable.ic_launcher, contentTitle, mConnectedDeviceName + ": ", readMessage);
                    }
                    break;
                case MESSAGE_DEVICE_NAME:
                    //save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    //mInfoTitle.setText("Connected "+mConnectedDeviceName);
                    Toast.makeText(getApplicationContext(), "connected to "+ mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST), Toast.LENGTH_SHORT).show();
                    mChatService.stop();
                    //开启监听
                    Log.e(TAG,"Start to listen...2.");
                    mChatService.start();
                    break;
            }
        }
    };

    private boolean isRunningForeground(Context context){
        ActivityManager am = (ActivityManager)context.getSystemService(context.ACTIVITY_SERVICE);
        ComponentName cn = am.getRunningTasks(1).get(0).topActivity;
        String currentPackageName = cn.getPackageName();
        if(!TextUtils.isEmpty(currentPackageName)&&currentPackageName.equals(getPackageName())){
            return true;
        }
        return false;
    }

    private void showNotification(int icon,String contentText, String contentTitle, String content){
        mNotification = new Notification(icon,contentText,System.currentTimeMillis());
        mNotification.defaults = Notification.DEFAULT_ALL;

        //点击通知栏后自动消失
        mNotification.flags = Notification.FLAG_AUTO_CANCEL;

        //点击通知栏后自动回来原来的activity
        Intent notificationIntent = new Intent(getApplicationContext(),BluetoothChat.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        PendingIntent pt= PendingIntent.getActivity(BluetoothChat.this, 0, notificationIntent,PendingIntent.FLAG_UPDATE_CURRENT);

        mNotification.setLatestEventInfo(this,contentTitle,content,pt);
        mNotificationManager.notify(NotificationID, mNotification);
    }

    public void onActivityResult(int requestCode, int resultCode,Intent data){
        if(D) Log.d(TAG,"onActivityResult "+resultCode + "requestCode :"+requestCode);

        switch(requestCode){
            case REQUEST_CONNECT_DEVICE:
                //When DeviceListActivity returns with a device to connect
                if(resultCode == Activity.RESULT_OK){
                    //Get the device MAC address
                    String address = data.getExtras().getString(DeviceslistActivity.EXTRA_DEVICE_ADDRESS);
                    //Get the BluetoothDevice object
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                    mChatService.connect(device);
                }
                break;
            case REQUEST_ENABLE_BT:
                //When the request to enable Bluetooth returns
                if(resultCode == Activity.RESULT_OK){
                    //Bluetooth is now enable, so set up a chat session
                    Log.d(TAG, "you select ok to open Bluetooth");
                    setupChat();
                }else{
                    //user did not enable Bluetooth or an error occured
                    Log.d(TAG,"BT not enbale");
                    Toast.makeText(this,R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){
            case R.id.id_scan:
                //launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(this, DeviceslistActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
                return true;
            case R.id.id_discoverable:
                //Ensure this device is discoverable by others
                ensureDiscoverable();
                return true;
            case R.id.id_disableBT:
                mBluetoothAdapter.disable();
                return true;
            case R.id.id_exit:
                if(mChatService!= null) mChatService.stop();
                finish();
        }
        return false;
    }
}
