package com.tshare.AppPrivacyCheckTool;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * @author 小学生
 */
public class Utils {
    static String TAG = "AppPrivacyCheck";
    static boolean logOpen = true;
    //规定每段显示的长度
    private static int LOG_MAXLENGTH = 2000;

    public static String method_invoke_currentTime(){
        Date date = new Date();
        SimpleDateFormat now = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss.sss", Locale.US);
        return now.format(date);
    }


    public static void myLog(String content){
        if(logOpen){
            //InfiniteLog(TAG,content);
            Log.i(TAG,content);
        }
    }
    public static void myErrorlog(String content){
        if(logOpen){
            Log.e(TAG,content);
            //InfiniteLog(TAG,content);
        }
    }

    /**
     * 该方法解决log最多显示4*1024长度这个问题
     * @param TAG tag
     * @param msg  msg
     */
    private static void InfiniteLog(String TAG, String msg) {
        int strLength = msg.length();
        int start = 0;
        int end = LOG_MAXLENGTH;
        for (int i = 0; i < 10000; i++) {
            //剩下的文本还是大于规定长度则继续重复截取并输出
            if (strLength > end) {
                Log.e(TAG + i, msg.substring(start, end));
                start = end;
                end = end + LOG_MAXLENGTH;
            } else {
                Log.e(TAG, msg.substring(start, strLength));
                break;
            }
        }
    }

    /**
     * byte转16进制字符串
     * @param bytes byte
     * @return string
     */
    public static String Bytes2Str(byte[] bytes){
        String hexStr = "0123456789ABCDEF";
        StringBuilder result = new StringBuilder();
        String hex;
        for(int i=0;i<bytes.length;i++){
            //字节高4位
            hex = String.valueOf(hexStr.charAt((bytes[i]&0xF0)>>4));

            //字节低4位
            hex += String.valueOf(hexStr.charAt(bytes[i]&0x0F));
            if(i>0){
                result.append(":").append(hex);
            }else {
                result.append(hex);
            }

        }
        return result.toString();

    }

    /**
     * IP地址转换，由16进制转换为常见格式
     * @param ipAddress  16进制IP
     * @return 常见格式IP
     */
    public static String convertIP(int ipAddress){
        String ipString = null;
        // 获得IP地址的方法一：
        if (ipAddress != 0) {
            ipString = ((ipAddress & 0xff) + "." + (ipAddress >> 8 & 0xff) + "." + (ipAddress >> 16 & 0xff) + "." + (ipAddress >> 24 & 0xff));

        }
        return ipString;
    }

    /**
     * @param obj obj
     * @return 判断obj是否为空,空返回true，非空返回false
     */
    public static boolean isEmpty(Object obj) {
        if (obj== null) {
            return true;
        }
        if ((obj instanceof String)) {
            return "".equals(((String)obj).trim());
        } else if (obj instanceof Map) {
            return ((Map)obj).isEmpty();
        } else if (obj instanceof Object[]) {
            Object[] object = (Object[])obj;
            if (object.length == 0) {
                return true;
            }
        } else if (obj instanceof Collection) {
            return ((Collection)obj).isEmpty();
        } else if (obj instanceof CharSequence) {
            return ((CharSequence)obj).length() == 0;
        }
        return false;
    }

    /**
     * 针对毫秒时间进行格式化
     * @param timestamp 毫秒
     * @return 格式化时间
     */
    public static String dataformat(long timestamp){
        //SimpleDateFormat dateformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SimpleDateFormat dataformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        return dataformat.format(timestamp);
    }
}
