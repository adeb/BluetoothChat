package com.example.xiehh.testexample;

import com.example.xiehh.testexample.BluetoothChat;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;


/**
 * Created by xiepublic BluetoothChatService(BluetoothChat , Handler ) {
    }hh on 2015/3/6.
 */
public class BluetoothChatService {
    //debugging
    private static final String TAG = "BluetoothChatService";
    private static final boolean D = true;

    //name for the SDP record when creating server socket
    private static final String NAME = "BluetoothChat";

    //Unique UUID for this application
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");//("fa87c0d0-afac-11de-8a39-0800200c9a66");

    //Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    public BluetoothChatService(Context context, Handler handler){
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
    }



    private synchronized void setState(int state){
        if(D) Log.d(TAG, "setState()" + mState + "->" + state);

        mState = state;

        //Give the new state to the Handler so the ui activity can update
        mHandler.obtainMessage(BluetoothChat.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    public synchronized int getState(){
        Log.d(TAG, "mState = "+mState);
        return mState;
    }

    public synchronized void start(){
        if(D) Log.d(TAG, "start");

        //Cancel any thread attempting to make a connection
        if(mConnectThread != null){
            mConnectThread.cancel();
            mConnectThread =null;
        }

        //Cancel any thread currently running a connection
        if(mConnectedThread != null){
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        //start the thread to listen on a BluetoothServerSock
        if(mAcceptThread == null){
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
        }

        setState(STATE_LISTEN);
    }

    public synchronized void connect(BluetoothDevice device){
        if(D) Log.e(TAG, "connect to :" + device);

        //Cancel any thread attempting to make a connection
        if(mState == STATE_CONNECTING){
            if(mConnectThread != null){
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        //Cancel any thread currently running a connection
        if(mConnectedThread != null){
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        //Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    public synchronized void connected(BluetoothSocket socket,BluetoothDevice device){
        if(D) Log.e(TAG,"Connected");

        //Cancel the thread that completed the connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Cancel the accept thread because we only want to connect to one device
        if (mAcceptThread != null) {mAcceptThread.cancel(); mAcceptThread = null;}

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        //Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(BluetoothChat.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothChat.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    public synchronized void stop(){
        if(D) Log.d(TAG, "stop");

        if(mConnectThread != null){
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if(mConnectedThread != null){
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if(mAcceptThread != null){
            mAcceptThread.cancel();
            mAcceptThread = null;
        }

        setState(STATE_NONE);
    }

    public void write(byte[] out){
        //Create temporary object
        ConnectedThread r;
        //Ysnchronize a copy of the ConnectedThread
        synchronized (this){
            if(mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }

        //Perform the write unsynchronized
        r.write(out);
    }

    private void connectionFailed(){
        setState(STATE_LISTEN);

        //Send a failure message back to the activity
        Message msg = mHandler.obtainMessage(BluetoothChat.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothChat.TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    private void connectionLost(){
        setState(STATE_LISTEN);

        //Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(BluetoothChat.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothChat.TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    private class AcceptThread extends Thread{
        //The local server socket
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread(){
            BluetoothServerSocket tmp = null;

            //Create a new listening server socket
            try{
                tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME,MY_UUID);
            }catch (IOException e){
                Log.e(TAG,"listen() failed", e);
            }

            mmServerSocket = tmp;
        }

        public void run(){
            if(D) Log.e(TAG,"Begin mAcceptThread" + this);

            setName("AcceptThread");

            BluetoothSocket socket = null;

            //Listen to the server socket if we're not connected
            while(mState != STATE_CONNECTED){
                try{
                    //This is a blocking call and will only return on a successful connection or an exception
                    socket = mmServerSocket.accept();

                }catch(IOException e){
                    Log.e(TAG, "accept() failed break", e);
                    break;
                }

                Log.e(TAG, "Bluetooth paried accepted");
                //if a connection was accepted
                if(socket != null){
                    synchronized (BluetoothChatService.this){
                        switch(mState){
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                //SITUATION NORMAL. START THE CONNECTED THREAD.
                                Log.e(TAG,"--->connected..");
                                connected(socket, socket.getRemoteDevice());
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                //Either not ready or already connected . Terminate now socket.
                                try{
                                    socket.close();
                                }catch(IOException e){
                                    Log.e(TAG, "Could not close unwanted socket" , e);
                                }
                                break;
                        }
                    }
                }
            }
            if(D) Log.i(TAG, "End mAcceptThread");
        }

        public void cancel(){
            if(D) Log.d(TAG,"Cancel "+this);
            try{
                mmServerSocket.close();
            }catch (IOException e){
                Log.e(TAG,"Close() of server failed", e);
            }
        }
    }

    private class ConnectThread extends Thread{
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device){
            mmDevice = device;
            BluetoothSocket tmp = null;
            Method m;
            //Get a BluetoothSocket for a connection with the given BluetoothDevice

            try{
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            }catch (IOException e){
                Log.e(TAG, "create() failed ", e);
            }

            /*
            try{
                m = device.getClass().getMethod("createRfcommSocket", new Class[]{int.class});
                try{
                    tmp = (BluetoothSocket)m.invoke(device, 1);
                }catch (IllegalArgumentException e){
                    e.printStackTrace();
                }
            }catch (SecurityException e){
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }*/
            mmSocket = tmp;
        }

        public void run(){
            Log.e(TAG, "Begin mConnectThread");
            setName("ConnectThread");

            //Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            //Make a connection to the BluetoothSocket
            try{
                //This is a blocking call and will only return on a successful connection or an exception
                mmSocket.connect();
            }catch (IOException e){
                Log.e(TAG,"connectFaild", e);
                connectionFailed();
                //close the socket
                try{
                    mmSocket.close();
                }catch (IOException e2){
                    Log.e(TAG, "Unable to close() socket during connection failure", e2);
                }

                //start the service over to resetart listening mode
                //连接不成功时开启accept来监听
                BluetoothChatService.this.start();
                return;
            }

            //Reset the ConnectThread because we're done
            synchronized (BluetoothChatService.this){
                mConnectThread=null;
            }

            //start the connected thread
            connected(mmSocket,mmDevice);
        }

        public void cancel(){
            try{
                mmSocket.close();
            }catch (IOException e){
                Log.e(TAG , "Close() of connect socket failed", e);
            }
        }
    }

    private class ConnectedThread extends Thread{
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket){
            Log.e(TAG,"Create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            //Get the BluetoothSocket input output streams
            try{
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            }catch(IOException e){
                Log.e(TAG, "Temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run(){
            Log.i(TAG, "Begin mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

            //Keep listening to the InputStream while connected
            while(true){
                try{
                    //Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    // Send the obtained bytes to the UI Activity
                    mHandler.obtainMessage(BluetoothChat.MESSAGE_READ, bytes, -1,buffer).sendToTarget();
                }catch (IOException e){
                    connectionLost();
                    break;
                }
            }
        }

        public void write(byte[] buffer){
            try{
                mmOutStream.write(buffer);

                //share the sent message back to the ui activity
                mHandler.obtainMessage(BluetoothChat.MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
            }catch (IOException e){
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel(){
            try{
                mmSocket.close();
            }catch (IOException e){
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
}
