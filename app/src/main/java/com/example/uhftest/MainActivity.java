package com.example.uhftest;

import java.io.File;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.R.fraction;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.serialport.SerialPortManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.uhf.api.EPC;
import com.uhf.api.Util;
import com.zistone.gpio.Gpio;
import com.zistone.uhf.ZstCallBackListen;
import com.zistone.uhf.ZstUHFApi;

public class MainActivity extends Activity implements OnItemClickListener {
	private final String TAG = "ZstUHFApi";
	private Button button_start;
	private ZstUHFApi mZstUHFApi;
	private RelativeLayout tab_inventory, tab_setting, tab_rw;
	private TextView textViewEPC, tag_count;
	private boolean in_set_param = false, isStart = false;
	private int button_setting_num = 0;
	private String lostTag = "";
	private String selectEPC = "";
	private ListView listViewData;
	private SharedPreferences sp;
	private ArrayList<EPC> listEPC;
	private ArrayList<Map<String, Object>> listMap;
	private String ss;
	private SoundPool soundPool;
	private final int STATE_NO_THING = 0, STATE_START_INVENTORY = 1,
			STATE_SET_POWER = 2, STATE_GET_POWER = 3, 
			STATE_SET_CHANNEL = 4, STATE_GET_CHANNEL = 5,
			STATE_SET_PARAM = 6, STATE_GET_PARAM = 7,
			STATE_READ_TAG = 8, STATE_WRITE_TAG = 9,
			STATE_SET_SELECT_COMMOND = 10, STATE_SET_SELECT_MODE = 11;
	
	
	private int m_opration = STATE_NO_THING;
	private int m_read_tag = 0;
	
	private int gpio1_num = 81, gpio2_num = 113;
	private String SerialName = "/dev/ttyHSL1";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		String model= android.os.Build.MODEL;
		if(model.contains("msm8953")){
			gpio1_num = 66;
			gpio2_num = 98;
			SerialName = "/dev/ttyHSL0";
		}
		m_read_tag = 0;
		sp = getSharedPreferences("UHF_SHRAE", MODE_PRIVATE);
		gpio1_num = Integer.parseInt(sp.getString("gpio1", String.valueOf(gpio1_num)));
		gpio2_num = Integer.parseInt(sp.getString("gpio2", String.valueOf(gpio2_num)));
//		mSerialName = sp.getString("serial_number", "ttyHSL0");
		SerialName = sp.getString("serial_number", SerialName);
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_rfid);
		button_setting_num = 0;
		mZstUHFApi = new ZstUHFApi(this, new MyZstUhfListen());
		soundPool= new SoundPool(10,AudioManager.STREAM_SYSTEM,5);
		soundPool.load(this,R.raw.msg,1);
		
	    listEPC = new ArrayList<EPC>();
		tab_inventory = (RelativeLayout) findViewById(R.id.tab_inventory);
		tab_setting = (RelativeLayout) findViewById(R.id.tab_setting);
		tab_rw  = (RelativeLayout) findViewById(R.id.tab_rw);
		tag_count = (TextView) findViewById(R.id.tag_count);
		listViewData = (ListView) findViewById(R.id.listView_data);
		listViewData.setOnItemClickListener(this);
		listViewData.setOnItemClickListener(new AdapterView.OnItemClickListener() {
		    @Override
		    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		    	EPC epcTag = new EPC();
		    	epcTag = listEPC.get(position);
		    	selectEPC = epcTag.getEpc();
		    	go_rwcard_view(selectEPC);
		    }
		});
		button_start = (Button) findViewById(R.id.button_start);
		
		mZstUHFApi.setModelPower(true, gpio1_num, gpio2_num);
		
		// 初始化声音池
		Util.initSoundPool(this, R.raw.msg);
		initViewSetting();
		initViewReadWrite();
	}
	
	// ---------------------------------TAB MAIN VIEW START -----------------------------//
	// 盘存
	public void on_button_start(View v) {
		Log.d(TAG, "isStart = "+ isStart);
		if (false == isStart) {
			if(mZstUHFApi != null && mZstUHFApi.startInventory(10000)){
				m_opration = STATE_START_INVENTORY;
				button_start.setText(getString(R.string.stop_scan));
				isStart = true;
			}
		} else {
			m_opration = STATE_NO_THING;
			if(mZstUHFApi != null && mZstUHFApi.stopInventory()){
				button_start.setText(getString(R.string.start_scan));
			}
			isStart = false;
		}
	}
	
	public void go_rwcard_view(String epc){
		if(epc != null){
			textViewEPC.setText(epc);
		}else{
			textViewEPC.setText("");
			selectEPC = "";
		}
		m_opration = STATE_NO_THING;
		if(mZstUHFApi != null && mZstUHFApi.stopInventory()){
			button_start.setText(getString(R.string.start_scan));
		}
		isStart = false;
		tab_inventory.setVisibility(View.GONE);
		tab_setting.setVisibility(View.GONE);
		tab_rw.setVisibility(View.VISIBLE);
	}
	
	public void on_button_rwcard(View v){
		go_rwcard_view(null);
	}
	
	public void on_button_exit(View v) {
		mZstUHFApi.setModelPower(false, gpio1_num, gpio2_num);
		System.exit(0);
	}

	public void on_button_clear(View v) {
		listEPC.removeAll(listEPC);
		listViewData.setAdapter(null);
	}
	// ---------------------------------TAB MAIN VIEW END -----------------------------//
	
	// ---------------------------------TAB SET PARAMER START -----------------------------//
	private TextView tv_info;
	private EditText edittext_pwr, edittext_thrd, edittext_SelParam, edittext_ptr, edittext_mask;
	private Spinner sp_country_set, sp_mixe_set, sp_ifamp_set, sp_truncate_set;
	private ArrayAdapter<String> sp_country_adapter, sp_mixe_adapter, sp_ifamp_adapter, sp_truncate_adapter;
	private int curArrValue;
	private String[] countryArr = {"China900MHz" , "China800MHz" , "America" , "Europe" , "Korea"};
	private byte[] countryArrValue = new byte[]{0x01 , 0x04 , 0x02 , 0x03 , 0x06};
	
	private int curMixeValue;
	private String[] MixerArr = {"0db" , "3db" , "6db" , "9db" , "12db", "15db", "16db"};
	private byte[] MixerArrValue = new byte[]{0x00 , 0x01 , 0x02 , 0x03 , 0x04, 0x05,  0x06};
	
	private int curTruncateValue = 0x00;
	private String[] TruncateArr = {"Disable truncation" , "Enable truncation"};
	
	private int curIfampValue;
	private String[] IfampArr = {"12db" , "18db" , "21db" , "24db" , "27db", "30db", "36db", "40db"};
	private byte[] IfampArrValue = new byte[]{0x00 , 0x01 , 0x02 , 0x03 , 0x04, 0x05,  0x06, 0x07};
	private int mThrd = 0;
	private int mSelParam;
	private byte[] mPtr;
	private byte[] mMask;
	private void initViewSetting() {
		tv_info = (TextView) findViewById(R.id.tv_info);
		this.edittext_pwr = (EditText) findViewById(R.id.edittext_pwr);
		this.edittext_thrd = (EditText) findViewById(R.id.edittext_thrd);
		this.edittext_SelParam = (EditText) findViewById(R.id.edittext_SelParam);
		this.edittext_ptr = (EditText) findViewById(R.id.edittext_ptr);
		this.edittext_mask = (EditText) findViewById(R.id.edittext_mask);
		edittext_thrd.setText("176");
		
		sp_country_set = (Spinner)findViewById(R.id.sp_country_set);
	    sp_country_adapter = new ArrayAdapter<String>(this, 
	    		android.R.layout.simple_dropdown_item_1line, countryArr);//工作区域设置适配器
	    sp_country_set.setAdapter(sp_country_adapter);
	    sp_country_set.setSelection(0);
	    sp_country_set.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
	        @Override
	        public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
	        	curArrValue = arg2;
	        }

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				// TODO Auto-generated method stub
				
			}
	    });

	    sp_mixe_set = (Spinner)findViewById(R.id.sp_mixer_set);
	    sp_mixe_adapter = new ArrayAdapter<String>(this, 
	    		android.R.layout.simple_dropdown_item_1line, MixerArr);//工作区域设置适配器
	    sp_mixe_set.setAdapter(sp_mixe_adapter);	    
	    sp_mixe_set.setSelection(2);
	    sp_mixe_set.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
	        @Override
	        public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
	        	curMixeValue = arg2;
	        }

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				// TODO Auto-generated method stub
				
			}
	    });
	    
	    sp_ifamp_set = (Spinner)findViewById(R.id.sp_ifamp_set);
	    sp_ifamp_adapter = new ArrayAdapter<String>(this, 
	    		android.R.layout.simple_dropdown_item_1line, IfampArr);//工作区域设置适配器
	    sp_ifamp_set.setAdapter(sp_ifamp_adapter);	    
	    sp_ifamp_set.setSelection(6);
	    sp_ifamp_set.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
	        @Override
	        public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
	        	curIfampValue = arg2;
	        }

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				// TODO Auto-generated method stub
				
			}
	    });
	    
	    sp_truncate_set = (Spinner)findViewById(R.id.sp_truncate_set);
	    sp_truncate_adapter = new ArrayAdapter<String>(this, 
	    		android.R.layout.simple_dropdown_item_1line, TruncateArr);//工作区域设置适配器
	    sp_truncate_set.setAdapter(sp_truncate_adapter);	    
	    sp_truncate_set.setSelection(0);
	    sp_truncate_set.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
	        @Override
	        public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
	        	Log.d(TAG, "arg2 = "+ arg2);
	        	if(arg2 == 0){
	        		curTruncateValue = 0x00;
	        	}else{
	        		curTruncateValue = 0x80;
	        	}
	        	
	        }

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				// TODO Auto-generated method stub
				
			}
	    });
	}
	
	private void on_button_setting() {
		Stop();
		try {
			Thread.sleep(100);
		} catch (Exception ex) {
		}
		
		tab_inventory.setVisibility(View.GONE);
		tab_setting.setVisibility(View.VISIBLE);
		tab_rw.setVisibility(View.GONE);
		
		if(mZstUHFApi != null){
			m_opration = STATE_GET_POWER;
			mZstUHFApi.getTransmissionPower();
			try {
				Thread.sleep(100);
			} catch (Exception ex) {
			}
			mZstUHFApi.getTransmissionPower();
		}
		
		edittext_pwr.setFocusable(true);
		edittext_pwr.setFocusableInTouchMode(true);
	}
	
	public void on_btn_pwr_back(View v) {
		in_set_param = false;
		button_setting_num = 0;
		tab_inventory.setVisibility(View.VISIBLE);
		tab_setting.setVisibility(View.GONE);
		tab_rw.setVisibility(View.GONE);
	}
	
	public void on_button_pwr(View v) {
		m_opration = STATE_SET_POWER;

		edittext_pwr.getText().toString();
		if(edittext_pwr.getText().toString().equals("")){
			tv_info.setText(getString(R.string.warn_power_null));
			return;
		}
		int power = Integer.parseInt(edittext_pwr.getText().toString());
		Log.e("RFID", "power = "+ power);
		if (power > 26 || power < 15) {
			tv_info.setText(getString(R.string.warn_power_out));
			return;
		}
		if(mZstUHFApi != null)
			mZstUHFApi.setTransmissionPower(power*100);
	}

	public void on_button_getpwr(View v) {
		m_opration = STATE_GET_POWER;
		if(mZstUHFApi != null)
			mZstUHFApi.getTransmissionPower();
	}
	
	public void on_bt_set_channel(View v) {
		m_opration = STATE_SET_CHANNEL;
		if(mZstUHFApi != null)
			mZstUHFApi.setWorkChannel(countryArrValue[curArrValue]);
	}
	
	public void on_bt_get_channel(View v) {
		m_opration = STATE_GET_CHANNEL;
		if(mZstUHFApi != null)
			mZstUHFApi.getWorkChannel();
	}
	
	public void on_bt_set_param(View v) {
		m_opration = STATE_SET_PARAM;
		edittext_thrd.getText().toString();
		if(edittext_thrd.getText().toString().equals("")){
			tv_info.setText(getString(R.string.input_null));
			return;
		}
		mThrd = Integer.parseInt(edittext_thrd.getText().toString());
		if(mZstUHFApi != null)
			mZstUHFApi.setModemsParam(MixerArrValue[curMixeValue], IfampArrValue[curIfampValue], mThrd);
	}
	
	public void on_bt_set_select(View v) {
		m_opration = STATE_SET_SELECT_COMMOND;
		edittext_SelParam.getText().toString();
		if(edittext_SelParam.getText().toString().equals("") || edittext_ptr.getText().toString().equals("") || edittext_mask.getText().toString().equals("")){
			tv_info.setText(getString(R.string.input_null));
			return;
		}
		mSelParam = Integer.parseInt(edittext_SelParam.getText().toString());
		mPtr = Util.hexStr2Str(edittext_ptr.getText().toString());
		mMask = Util.hexStr2Str(edittext_mask.getText().toString());
		if(mZstUHFApi != null)
			mZstUHFApi.setSelectCommend(mSelParam, mPtr, curTruncateValue, mMask);
	}
	
	public void on_bt_get_param(View v) {
		m_opration = STATE_GET_PARAM;
		if(mZstUHFApi != null)
			mZstUHFApi.getModemsParam();
	}
	
	public void on_button_back(View v) {
		tab_inventory.setVisibility(View.VISIBLE);
		tab_rw.setVisibility(View.GONE);
		tab_setting.setVisibility(View.GONE);
	}
	// ---------------------------------TAB SET PARAMER END -----------------------------//
	
	// ---------------------------------TAB READ WRITE RFID START---------------------//
	private Spinner spinnerMemBank;// 数据区
	private EditText editPassword;// 密码
	private EditText editAddr;// 起始地址
	private EditText editLength;// 读取的数据长度
	private EditText editWriteData;// 要写入的数据
	private EditText editReadData;// 读取数据展示区
	private final String[] strMemBank = { "RESERVE", "EPC", "TID", "USER" };//USER分别对应0,1,2,3
	private ArrayAdapter<String> adatpterMemBank;
	private int membank;// 数据区
	private int addr = 0;// 起始地址
	private int length = 1;// 读取数据的长度
	private String accessPassword;
	private String writeData;

	private void initViewReadWrite() {
		this.spinnerMemBank = (Spinner) findViewById(R.id.spinner_membank);
		this.textViewEPC = (TextView) findViewById(R.id.textViewEPC);
		this.editAddr = (EditText) findViewById(R.id.edittext_addr);
		this.editLength = (EditText) findViewById(R.id.edittext_length);
		this.editPassword = (EditText) findViewById(R.id.editTextPassword);
		this.editWriteData = (EditText) findViewById(R.id.edittext_write);
		this.editReadData = (EditText) findViewById(R.id.linearLayout_readData);
		this.adatpterMemBank = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item, strMemBank);
		this.adatpterMemBank
				.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		this.spinnerMemBank.setAdapter(adatpterMemBank);
		spinnerMemBank.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1,
					int arg2, long arg3) {
				membank = arg2;
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				// TODO Auto-generated method stub
			}
		});
	}
	
	public void on_button_read(View v) {
		m_opration = STATE_READ_TAG;
		m_read_tag = 1;
		accessPassword = editPassword.getText().toString();
		addr = Integer.valueOf(editAddr.getText().toString());
		length = Integer.valueOf(editLength.getText().toString());

		if (accessPassword.length() != 8) {
			Toast.makeText(getApplicationContext(), "The password is 4 bytes",
					Toast.LENGTH_SHORT).show();
			return;
		}
		Log.d(TAG, "read selectEPC = "+selectEPC);
		if(selectEPC.length() <= 0){
			if(mZstUHFApi != null)
				mZstUHFApi.readCradTag(Util.hexStr2Str(accessPassword), (byte)membank, addr, length);
		}else{
			mHandler.removeMessages(1);
			mHandler.sendEmptyMessage(1);
		}
	}

	public void on_button_write(View v) {
		m_opration = STATE_WRITE_TAG;
		m_read_tag = 2;
		writeData = editWriteData.getText().toString();
		accessPassword = editPassword.getText().toString();
		addr = Integer.valueOf(editAddr.getText().toString());
		length = Integer.valueOf(editLength.getText().toString());

		if (accessPassword.length() != 8) {
			Toast.makeText(getApplicationContext(), "The password is 4 bytes",
					Toast.LENGTH_SHORT).show();
			return;
		}
		if (0 == membank) {
			Toast.makeText(getApplicationContext(), "Reservations are not allowed to write",
					Toast.LENGTH_SHORT).show();
			return;
		}
		if (2 == membank) {
			Toast.makeText(getApplicationContext(), "TID are not allowed to write",
					Toast.LENGTH_SHORT).show();
			return;
		}
		if (writeData.length() != length * 4) {
			Toast.makeText(getApplicationContext(), "The length of data is not consistent with the length of data written",
					Toast.LENGTH_SHORT).show();
			return;
		}
		Log.d(TAG, "length = "+ length);
		Log.d(TAG, "writeData = "+ writeData);
		Log.d(TAG, "write selectEPC = "+selectEPC);
		if(selectEPC.length() <= 0){
			if(mZstUHFApi != null)
				mZstUHFApi.writeCradTag(Util.hexStr2Str(accessPassword), (byte)membank, addr, length, Util.hexStr2Str(writeData));
		}else{
			mHandler.removeMessages(1);
			mHandler.sendEmptyMessage(1);
		}
	}

	public void on_button_backrw(View v) {
		tab_inventory.setVisibility(View.VISIBLE);
		tab_rw.setVisibility(View.GONE);
		tab_setting.setVisibility(View.GONE);
	}

	public void on_button_readClear(View v) {
		editReadData.setText("");
	}
	// ---------------------------------TAB READ WRITE RFID START---------------------//
	
	
	// 将读取的EPC添加到LISTVIEW
	private void addToList(final List<EPC> list, final String epc) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				// 第一次读入数据
				if (list.isEmpty()) {
					EPC epcTag = new EPC();
					epcTag.setEpc(epc);
					epcTag.setCount(1);
					list.add(epcTag);
				} else {
					for (int i = 0; i < list.size(); i++) {
						EPC mEPC = list.get(i);
						// list中有此EPC
						if (epc.equals(mEPC.getEpc())) {
							mEPC.setCount(mEPC.getCount() + 1);
							list.set(i, mEPC);
							break;
						} else if (i == (list.size() - 1)) {
							// list中没有此epc
							EPC newEPC = new EPC();
							newEPC.setEpc(epc);
							newEPC.setCount(1);
							list.add(newEPC);
						}
					}
				}
				// 将数据添加到ListView
				listMap = new ArrayList<Map<String, Object>>();
				int idcount = 1;
				for (EPC epcdata : list) {
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("ID", idcount);
					map.put("EPC", epcdata.getEpc());
					map.put("COUNT", epcdata.getCount());
					idcount++;
					listMap.add(map);
				}
				listViewData.setAdapter(new SimpleAdapter(MainActivity.this, listMap,
						R.layout.listview_item, new String[] { "ID", "EPC",
								"COUNT" }, new int[] { R.id.textView_id,
								R.id.textView_epc, R.id.textView_count }));
				ShowCount();
			}
		});
	}
	
	private void ShowCount() {
		tag_count.setText("Tag count:" + listEPC.size());
	}

	private void onDataReceived(final byte[] buffer, final int size) {
		ss = lostTag;
		int endTag = 1;
		Log.d(TAG,"Recv_Buffer = "+ Util.byte2hex(buffer, size));
		for (int i = 0; i < size; i++) {
			String oneByte = Util.toHex(buffer[i]);
			if (oneByte.equals("BB")) {
				if (endTag == 1)
					ss = Util.toHex(buffer[i]) + " ";
				else {
					ss += Util.toHex(buffer[i]) + " ";
					endTag = 0;
				}
			} else {
				ss += Util.toHex(buffer[i]) + " ";
				endTag = 0;
			}
			if (oneByte.equals("7E")) {
				endTag = 1;
				if (true == Util.checkSum(ss)) {
					Log.d(TAG, "m_opration = "+m_opration);
					if(m_opration == STATE_START_INVENTORY){
						if (ss.length() > 52) {
							String data = ss.trim().replaceAll(" ", "");
							int pl_h = Util.toInt(data.substring(6, 8));
							int pl_l = Util.toInt(data.substring(8, 10));
							int plen = (pl_h*256+pl_l)*2;
							int RSSI = Util.toInt(data.substring(10, 12));
							int pc_h = Util.toInt(data.substring(12, 14));
							int pc_l = Util.toInt(data.substring(14, 16));
							int pc = pc_h*256+pc_l;
							String epc = "";
//							Log.d(TAG, "data.length() = "+ data.length());
//							Log.d(TAG, "plen = "+ plen);
							if(plen >= 10 && data.length() >= 16+plen-10){
								epc = data.substring(16, 16+plen-10);
								Log.d(TAG, "epc = "+ epc);
							}
							addToList(listEPC, epc);
							soundPool.play(1, 1, 1, 0, 0, 1);
						} else {// EPC不够	
							int len = Util
									.toInt(ss.substring(12, 14));
							if (len > 5) {
								len = len - 5;
								try {
									Thread.sleep(1); // yinbo remove
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
							}
						}
					}
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							String recvStr = ss.trim().replaceAll(" ", "");
							switch (m_opration) {
								case STATE_SET_POWER:// 设置功率
									if (recvStr.substring(4, 6).equals("B6")){
										tv_info.setText(getString(R.string.set_power_success));
									} else {
										tv_info.setText(getString(R.string.set_power_fail));
									}
									break;
								case STATE_GET_POWER:// 获取功率
									if (recvStr.substring(4, 6).equals("B7")){
										tv_info.setText(getString(R.string.get_power_success));
										edittext_pwr.setText(Util.GetPower(ss.trim()));
									} else {
										tv_info.setText(getString(R.string.get_power_fail));
									}
									break;
								case STATE_SET_CHANNEL://设置工作信道
									if (recvStr.substring(4, 6).equals("AB")){
										tv_info.setText(getString(R.string.set_channel_success));
									} else {
										tv_info.setText(getString(R.string.set_channel_fail));
									}
									break;
								case STATE_GET_CHANNEL://设置工作信道
									if (recvStr.substring(4, 6).equals("AA")){
										tv_info.setText(getString(R.string.get_channel_success));
//										curArrValue = Integer.parseInt(recvStr.substring(10, 12));
//										sp_country_set.setSelection(curArrValue);
									} else {
										tv_info.setText(getString(R.string.get_channel_fail));
									}
									break;
								case STATE_SET_PARAM://设置模块参数
									if (recvStr.substring(4, 6).equals("F0")){
										tv_info.setText(getString(R.string.set_param_success));
									} else {
										tv_info.setText(getString(R.string.set_param_fail));
									}
									break;
								case STATE_GET_PARAM://获取模块参数
									if (recvStr.substring(4, 6).equals("F1")){
										tv_info.setText(getString(R.string.get_param_success));
										if(recvStr.length() >= 12){
											curMixeValue = Integer.parseInt(recvStr.substring(10, 12));
											sp_mixe_set.setSelection(curMixeValue);
										}
										if(recvStr.length() >= 14){
											curIfampValue = Integer.parseInt(recvStr.substring(12, 14));
											sp_ifamp_set.setSelection(curIfampValue);
										}
										if(recvStr.length() >= 18){
											String thrd_str = recvStr.substring(14, 18);
											if(thrd_str != null){
												Log.d(TAG, "thrd_str = "+thrd_str);
												if(recvStr.length() >= 4){
													int thred_h = Util.toInt(thrd_str.substring(0,2));
													int thred_l = Util.toInt(thrd_str.substring(2,4));
													Log.d(TAG, "thred_h = "+thred_h);
													Log.d(TAG, "thred_l = "+thred_l);
													mThrd = thred_h*256 + thred_l;
													Log.d(TAG, "mThrd = "+mThrd);
													edittext_thrd.setText(String.valueOf(mThrd));
												}
											}
										}
									} else {
										tv_info.setText(getString(R.string.get_param_fail));
									}
									break;
								case STATE_READ_TAG:// 读标签
									if (recvStr.substring(4, 6).equals("39")) {
										// 读取成功
										if (getResources().getConfiguration().locale
												.getCountry().equals("CN")){
											textViewEPC.setText(recvStr.substring(8*2, (8+12)*2));
											editReadData.append("读取:"
													+ recvStr.substring(recvStr.length()
															- 4 - length * 4,
															recvStr.length() - 4) + "\n");
										}
										else{
											textViewEPC.setText(recvStr.substring(8*2, (8+12)*2));
											editReadData.append("Read:"
													+ recvStr.substring(recvStr.length()
															- 4 - length * 4,
															recvStr.length() - 4) + "\n");
										}
									} else {
										// 读取失败
										if (getResources().getConfiguration().locale
												.getCountry().equals("CN"))
											editReadData.append("读取失败 \n");
										else
											editReadData.append("Read Fail \n");
									}
									if(m_read_tag == 4){
										mHandler.removeMessages(m_read_tag);
										mHandler.sendEmptyMessage(m_read_tag);
									}
									break;
								case STATE_WRITE_TAG:// 写标签
									if (recvStr.substring(4, 6).equals("49")) {
										// 读取成功
										if (getResources().getConfiguration().locale
												.getCountry().equals("CN"))
											editReadData.append("写入成功" + "\n");
										else
											editReadData.append("Write Success" + "\n");
									} else {
										// 读取失败
										if (getResources().getConfiguration().locale
												.getCountry().equals("CN"))
											editReadData.append("写入失败 \n");
										else
											editReadData.append("Write Fail \n");
									}
									if(m_read_tag == 4){
										mHandler.removeMessages(m_read_tag);
										mHandler.sendEmptyMessage(m_read_tag);
									}
									break;
								case STATE_SET_SELECT_MODE:
									if(m_read_tag != 0){
										Log.d(TAG, "select mode success");
										mHandler.removeMessages(2);
										mHandler.sendEmptyMessage(2);
									}
									break;
								case STATE_SET_SELECT_COMMOND:
									if(m_read_tag != 0){
										Log.d(TAG, "select commod success");
										mHandler.removeMessages(3);
										mHandler.sendEmptyMessage(3);
									}
									break;
								default:break;
							}
						}
					});
				}else{
					Log.e(TAG, "checkSum if fail!!!");
				}
			}
		}
		lostTag = ss;
	}
	
	public class MyZstUhfListen implements ZstCallBackListen{
		@Override
		public void onUhfReceived(byte[] data, int len) {
			// TODO Auto-generated method stub
			onDataReceived(data, len);
		}
	}
	
	private void DisplayError(int resourceId) {
		AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);  
		builder.setTitle("Error");  
		builder.setMessage(resourceId);  
		builder.setPositiveButton("ok", new DialogInterface.OnClickListener() {
			@Override  
			public void onClick(DialogInterface dialog, int which) {
				mZstUHFApi.closeDevice();
			}  
		});  
		AlertDialog dialog = builder.create(); 
		dialog.show(); 
	}
	
	private int openScanDevice(){
		String path = sp.getString("DEVICE", SerialName);
		int baud_rate = Integer.decode(sp.getString("BAUDRATE", getString(R.string.baud_rate_def)));
		int data_bits = Integer.decode(sp.getString("DATA", getString(R.string.data_bits_def)));
		int stop_bits = Integer.decode(sp.getString("STOP", getString(R.string.stop_bits_def)));
		int flow = 0;
		int parity = 'N';
		String flow_ctrl = sp.getString("FLOW", getString(R.string.flow_control_def));
		String parity_check = sp.getString("PARITY", getString(R.string.parity_check_def));
		Log.d(TAG, "baud_rate = "+baud_rate);
		/* Check parameters */
		if ( (path.length() == 0) || (baud_rate == -1)) {
			throw new InvalidParameterException();
		}
		Log.d(TAG, "path = " + path);
		if(flow_ctrl.equals("RTS/CTS"))
			flow = 1;
		else if(flow_ctrl.equals("XON/XOFF"))
			flow = 2;
		
		if(parity_check.equals("Odd"))
			parity = 'O';
		else if(parity_check.equals("Even"))
			parity = 'E';
		
		int retOpen = -1;
		if(mZstUHFApi != null){
			retOpen = mZstUHFApi.opendevice(
					new File(path), baud_rate, flow,
					data_bits, stop_bits, parity, gpio1_num);
		}
		Log.d(TAG, "retOpen = "+retOpen);
//		btn_scan_one.setEnabled(false);
//		btn_scan_continuous.setEnabled(false);
		if(retOpen == SerialPortManager.RET_OPEN_SUCCESS || 
				retOpen == SerialPortManager.RET_DEVICE_OPENED){
//			btn_scan_one.setEnabled(true);
//			btn_scan_continuous.setEnabled(true);
//			isOpened = true;
		}
		else if(retOpen == SerialPortManager.RET_NO_PRTMISSIONS){
			DisplayError(R.string.error_security);
		}else if(retOpen == SerialPortManager.RET_ERROR_CONFIG){
			DisplayError(R.string.error_configuration);
		}else{
			DisplayError(R.string.error_unknown);
		}
		return retOpen;
	}

	@Override
	protected void onStart() {
		super.onStart();
		openScanDevice();
	}

	@Override
	protected void onStop() {
		super.onStop();
		Stop();
		if(mZstUHFApi != null)
			mZstUHFApi.closeDevice();
	}

	@Override
	public void onDestroy() {
		mZstUHFApi.setModelPower(false, gpio1_num, gpio2_num);
		super.onDestroy();
		System.exit(0);
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
	    if ((keyCode == KeyEvent.KEYCODE_HOME || 
	    		keyCode == KeyEvent.KEYCODE_BACK ) &&
	    		event.getRepeatCount() == 0) {
	    	in_set_param = false;
	    	mZstUHFApi.setModelPower(false, gpio1_num, gpio2_num);
	    	System.exit(0);
	        return true;
	    }
	    return super.onKeyDown(keyCode, event);
	}

	private void Stop() {
		if (isStart) {
			m_opration = STATE_NO_THING;
			if(mZstUHFApi != null)
				mZstUHFApi.stopInventory();
			button_start.setText(getString(R.string.start_scan));
			isStart = false;
		}
	}

	@Override
	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
		// TODO Auto-generated method stub
		
	}
	
   @Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
    
    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
    	// TODO Auto-generated method stub
    	if(featureId == 0){
    		if(!in_set_param){
	    		in_set_param = true;
	    		on_button_setting();
        	}
    		if(button_setting_num < 6){
	    		button_setting_num++;
	    	}else{
	    		button_setting_num = 1;
	    		GetGpioNumberAndSerialNumber();
	    	}
    	}
    	return super.onMenuItemSelected(featureId, item);
    }
    
    
    private void GetGpioNumberAndSerialNumber() {
    	final TextView tv1 = new TextView(this); 
		final EditText et1 = new EditText(this);  
		View view = getLayoutInflater().inflate(R.layout.dialog_layout,null); 
		final EditText et_gpio1 = (EditText) view.findViewById(R.id.et_gpio1);
		final EditText et_gpio2 = (EditText) view.findViewById(R.id.et_gpio2);
		final EditText et_serial_num = (EditText) view.findViewById(R.id.et_serial_num);
		
		et_gpio1.setText(String.valueOf(gpio1_num));
		et_gpio2.setText(String.valueOf(gpio2_num));
		et_serial_num.setText(SerialName);
		
		AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);  
		builder.setTitle("Set UHF Paramer");  
		builder.setView(view);
		builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {   
			@Override  
			public void onClick(DialogInterface dialog, int which) {
			}  
		});  
		builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
			@Override  
			public void onClick(DialogInterface dialog, int which) {
				SerialName = et_serial_num.getText().toString();
				gpio1_num = Integer.parseInt(et_gpio1.getText().toString());
				gpio2_num = Integer.parseInt(et_gpio2.getText().toString());   
				Editor editor=sp.edit();
				editor.putString("gpio1", et_gpio1.getText().toString());
				editor.putString("gpio2", et_gpio2.getText().toString());
				editor.putString("serial_number", SerialName);
				editor.commit();
			}
		});
		AlertDialog dialog = builder.create(); 
		dialog.show(); 
	}
    
    private Handler mHandler = new Handler(){
		public void handleMessage(android.os.Message msg) {
			Log.d(TAG, "msg.what = "+ msg.what);
			switch(msg.what){
				case 1://s设置锁定模式
					if(mZstUHFApi != null){
						m_opration = STATE_SET_SELECT_MODE;
						mZstUHFApi.setSelectMode(0x02);
					}
					break;
				case 2://设置Select参数指令
					if(mZstUHFApi != null){
						m_opration = STATE_SET_SELECT_COMMOND;
						byte[] ptr = new byte[]{0x00, 0x00, 0x00, 0x20};
						mZstUHFApi.setSelectCommend((byte)membank, ptr, 0x00, Util.hexStr2Str(selectEPC));
					}
					break;
				case 3:
					if(m_read_tag == 1){
						if(mZstUHFApi != null){
							m_opration = STATE_READ_TAG;
							mZstUHFApi.readCradTag(Util.hexStr2Str(accessPassword), (byte)membank, addr, length);
							m_read_tag = 4;
						}
					}else if(m_read_tag == 2){
						if(mZstUHFApi != null){
							m_opration = STATE_WRITE_TAG;
							mZstUHFApi.writeCradTag(Util.hexStr2Str(accessPassword), (byte)membank, addr, length, Util.hexStr2Str(writeData));
							m_read_tag = 4;
						}
					}
					break;
				case 4://取消锁定模式
					if(mZstUHFApi != null){
						m_read_tag = 0;
						m_opration = STATE_SET_SELECT_MODE;
						mZstUHFApi.setSelectMode(0x01);
					}
					break;
				default:
					break;
			}
		};
	};
    
}
