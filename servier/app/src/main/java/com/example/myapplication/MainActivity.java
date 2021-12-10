package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int CONN_SUCCESS = 0x1;
    private static final int CONN_FAIL = 0x2;
    private static final int RECEIVER_INFO = 0x3;
    private static final int SET_EDITTEXT_NULL = 0x4;
    private static Button send;
    private static TextView server_state;
    private static EditText server_send;

    BluetoothAdapter bluetooth = null;//本地蓝牙设备
    BluetoothServerSocket serverSocket = null;//蓝牙设备Socket服务端
    BluetoothSocket socket = null;//蓝牙设备Socket客户端

    //输入输出流
    PrintStream out;
    BufferedReader in;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setTitle("蓝牙服务端");
        server_state = (TextView) findViewById(R.id.server_state);
        server_send = (EditText) findViewById(R.id.server_send);
        send = (Button) findViewById(R.id.send);
        init();
    }

    //创建蓝牙服务器端的Socket
    private void init() {
        server_state.setText("服务器已启动，正在等待连接...\n");
        new Thread(new Runnable() {
            @Override
            public void run() {
                //1.得到本地设备
                bluetooth = BluetoothAdapter.getDefaultAdapter();
                //2.创建蓝牙Socket服务器
                try {
                    serverSocket = bluetooth.listenUsingRfcommWithServiceRecord("text", UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                    //3.阻塞等待Socket客户端请求
                    socket = serverSocket.accept();
                    if (socket != null) {
                        out = new PrintStream(socket.getOutputStream());
                        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    }
                    handler.sendEmptyMessage(CONN_SUCCESS);
                } catch (IOException e) {
                    e.printStackTrace();
                    Message msg = handler.obtainMessage(CONN_FAIL, e.getLocalizedMessage());
                    handler.sendMessage(msg);
                }

            }
        }).start();

    }

    //防止内存泄漏 正确的使用方法
    private final MyHandler handler = new MyHandler(this);

    public class MyHandler extends Handler {
        //软引用
        WeakReference<MainActivity> weakReference;

        public MyHandler(MainActivity activity) {
            weakReference = new WeakReference<MainActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            MainActivity activity = weakReference.get();
            if (activity != null) {
                switch (msg.what) {
                    case RECEIVER_INFO:
                        setInfo(msg.obj.toString() + "\n");
                        break;
                    case SET_EDITTEXT_NULL:
                        server_send.setText("");
                        break;
                    case CONN_SUCCESS:
                        setInfo("连接成功！\n");
                        send.setEnabled(true);
                        new Thread(new ReceiverInfoThread()).start();
                        break;
                    case CONN_FAIL:
                        setInfo("连接失败！\n");
                        setInfo(msg.obj.toString() + "\n");
                        break;
                    default:
                        break;
                }
            }
        }
    }

    private boolean isReceiver = true;

    class ReceiverInfoThread implements Runnable {
        @Override
        public void run() {
            String info = null;
            while (isReceiver) {
                try {
                    info = in.readLine();
                    Message msg = handler.obtainMessage(RECEIVER_INFO, info);
                    handler.sendMessage(msg);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void send(View v) {
        final String content = server_send.getText().toString();
        if (TextUtils.isEmpty(content)) {
            Toast.makeText(MainActivity.this, "不能发送空消息", Toast.LENGTH_LONG).show();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                out.println(content);
                out.flush();
                handler.sendEmptyMessage(SET_EDITTEXT_NULL);
            }
        }).start();
    }

    private void setInfo(String info) {
        StringBuffer sb = new StringBuffer();
        sb.append(server_state.getText());
        sb.append(info);
        server_state.setText(sb);
    }
}