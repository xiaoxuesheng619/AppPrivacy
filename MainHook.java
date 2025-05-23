package com.tshare.AppPrivacyCheckTool;

import android.annotation.SuppressLint;
import android.app.AndroidAppHelper;
import android.app.Application;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.location.Location;
import android.os.Build;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import com.alibaba.fastjson.JSONObject;

import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;


/**
 * @author 小学生
 */
public class MainHook implements IXposedHookLoadPackage {

    private String ToolSelf_PACKAGENAME = "com.tshare.AppPrivacyCheckTool";
    /**
     * 被hook App的context
     */
    private Context appContext = null;
    /**
     * Android 系统context,初始化为null
     */
    Context systemContext = null;

    /**
     * 用来存储从目标App中通过反射获取的okhttp3.OkHttpClient类对象
     */
    private Class<?> OkHttpClientClz = null;
    /**
     * 用来存储从目标App中通过反射获取的okhttp3.Request类对象
     */
    private Class<?> RequestClz = null;

    ClassLoader AppClassloader;
    ClassLoader ServiceClassloader;
    private ArrayList<ClassLoader> AppAllCLassLoaderList = new ArrayList<>();
    private ArrayList<String> AppAllCLassLoaderStringList = new ArrayList<>();
    /**
     * DO_NOT_NEED_RETURN表示不需要返回值
     */
    public final String DO_NOT_NEED_RETURN="-";
    /**
     * 弹框记录，只记录第一次弹框
     */
    boolean showOnceBool = true;
    /**
     * 目标App包名，从content provider中读出来的
     */
    String targetAppPackageName;
    /**
     * 当前启动的App包名
     */
    String currentPackageName;
    /**
     * 当前启动App的进程名
     */
    String currentprocessName;

    /**
     * @desc 每个App应用启动的时候会调用该方法，如果是多进程应用，每个进程启动的时候也都会调用
     * @param loadPackageParam 该类下包含正在启动的App相关信息
     * @throws Throwable 异常
     */
    @SuppressLint("PrivateApi")
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        currentPackageName = loadPackageParam.packageName;
        currentprocessName = loadPackageParam.processName;
        AppClassloader = loadPackageParam.classLoader;

        //当任意App的主进程或子进程启动时，输出包名、进程名、加载系统包及应用包默认的classloader
        Utils.myLog("App启动信息(包名:"+currentPackageName + "  线程名:"+currentprocessName+ " 应用classloader:"+ AppClassloader.getClass().getName()+")");


        if("android".equals(loadPackageParam.packageName)){
            ServiceClassloader = AppClassloader;
            //insertServiceClassloaderToProvider(ServiceClassloader,getContextFromSystemContext(AppClassloader));
            Utils.myErrorlog("ServiceClassloader:"+ServiceClassloader);
            //hookMethodBroadcast(ServiceClassloader,"自启动");
        }
        //排除掉Android系统App、Android系统更新APP、xposed插件自身App，不用hook这些App
        if (loadPackageParam.appInfo == null
                || (loadPackageParam.appInfo.flags & (ApplicationInfo.FLAG_SYSTEM |ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                || (ToolSelf_PACKAGENAME.equals(currentPackageName) )) {
            Utils.myLog(currentPackageName+"属于系统App或系统更新App或插件App自身启动，不执行hook程序，直接返回.");
            return;
        }

        /*
            Android 9及以下使用系统级别(android)的context调用content provider；
            Android 10(含)以上无法通过系统context调用content provider，需要获取app的context
        */
        if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.P){
            Utils.myLog("SDK版本："+Build.VERSION.SDK_INT+" 获取系统context");
            //避免重复主动调用
            if(systemContext == null){
                getSystemContext(AppClassloader);
            }
            startHook(systemContext,AppClassloader);
        }else {
            Utils.myLog("SDK版本："+Build.VERSION.SDK_INT+",需要使用App的context");

            getAppContext(AppClassloader);

        }
    }



    private Context getSystemContext(ClassLoader cls){
        Class activityThreadClz = XposedHelpers.findClass("android.app.ActivityThread", cls);
        Object cob = XposedHelpers.callStaticMethod(activityThreadClz, "currentActivityThread");
        // 传入指定值
        systemContext = (Context) XposedHelpers.callMethod(cob, "getSystemContext");
        //Context systemContext = (Context) XposedHelpers.callMethod(XposedHelpers.callStaticMethod( XposedHelpers.findClass("android.app.ActivityThread", loadPackageParam.classLoader),"currentActivityThread"), "getSystemContext" );
        Utils.myLog("system context:"+systemContext);
        return systemContext;
    }




    private void getContextByHookContextWrapper(ClassLoader cls){
        try{
            Class<?>ContextClass=XposedHelpers.findClass("android.content.ContextWrapper",cls);
            XposedHelpers.findAndHookMethod(ContextClass,"getApplicationContext",new XC_MethodHook(){
                @Override
                protected void afterHookedMethod(MethodHookParam param)throws Throwable{
                    super.afterHookedMethod(param);
                    Context applicationContext=(Context)param.getResult();
                    Utils.myLog("applicationContext:"+applicationContext);
                    if(applicationContext !=null){
                        startHook(applicationContext,cls);
                    }else {
                        Utils.myErrorlog("myContext is null");
                    }
                }
            });
        }catch(Throwable e){
            Utils.myErrorlog("获取上下文出错："+e);
        }
    }



    private void getAppContext(ClassLoader cls){
        Class<?>clsInstrumentation=XposedHelpers.findClass("android.app.Instrumentation",cls);
        XposedBridge.hookAllMethods(clsInstrumentation,"newApplication",new XC_MethodHook(){
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable
            {
                super.afterHookedMethod(param);//这句调用意义不大，因为本来就是空函数
                Utils.myErrorlog("newApplication被调用了，注意查看是否存在重复hook的情况");


                appContext =(Context)param.getResult();
                Utils.myLog("appContext:"+appContext);
                if(appContext !=null){
                    startHook(appContext,cls);
                }else {
                    Utils.myErrorlog("myContext is null");
                }
            }
        });

    }


    /**
     * 根据获取到的context读取Content Provider中的包名，判断是否和目标App匹配，如果匹配，则执行mainHook
     * App存在子进程时候，子进程所在的包名和检测的目标App包名可能不一样，
     * @param ctx context
     * @param cls classloader
     */
    private void startHook(Context ctx,ClassLoader cls){
        targetAppPackageName = getTargetAppFromContentProvider(ctx);
        if(targetAppPackageName != null){

            if(currentPackageName.contains(targetAppPackageName)){
                Utils.myLog("当前启动App进程："+currentPackageName + " 和目标App包名匹配.");
                appStartInfo(ctx,currentPackageName,currentprocessName);
                mainHook(cls,ctx);
            }else {
                Utils.myLog("当前启动App进程："+currentPackageName + " 和目标App包名: "+targetAppPackageName+"不匹配.");
            }
        }else {
            Utils.myErrorlog("TargetApp为null.");
        }
    }



    /**
     * 主hook方法
     * @param clzloader  class loader
     * @param context context
     */
    private void mainHook(ClassLoader clzloader,Context context){
        Utils.myLog("正式开启hook啦.......");
        //hookMethodloadClass();
        hookMethodWithOnlyResult("android.app.Dialog",clzloader,"show","弹出隐私政策授权框",context);//通过弹框展示隐私政策，还有就是通过webview展示隐私政策，本工具没有覆盖webview类型
        hookMethodWithOnlyResult("android.content.ClipboardManager",clzloader,"getPrimaryClip","读取剪贴板",context);
        hookMethodWithOnlyResult("java.net.NetworkInterface",clzloader,"getInetAddresses","获取IP地址",context);
        //hookMethodWithOnlyResult("java.net.Inet4Address",clzloader,"getHostAddress","获取IP地址",context);
        hookMethodWithOnlyResult("android.net.wifi.WifiInfo",clzloader,"getIpAddress","获取IP地址",context);
        hookMethodWithOnlyResult("android.telephony.TelephonyManager",clzloader,"getLine1Number","获取手机号",context);
        hookMethodWithOnlyResult("android.telephony.TelephonyManager",clzloader,"getSimSerialNumber","获取SIM序列号(ICCID)",context);
        hookMethodWithOnlyResult("android.telephony.TelephonyManager",clzloader,"getSubscriberId","获取国际移动用户识别码(IMSI)",context);
        hookMethodWithOnlyResult("android.telephony.TelephonyManager",clzloader,"getNeighboringCellInfo","获取位置信息(读取相邻基站)",context);

        hookMethodWithOnlyResult("android.media.MediaRecorder",clzloader,"start","开始录音",context);
        //hookMethodWithOnlyResult("com.google.android.gms.ads.identifier.AdvertisingIdClient$Info",clzloader,"getId","获取GAID",context);
        hookMethodWithOnlyResult("com.bun.miitmdid.pojo.IdSupplierImpl",clzloader,"getOAID","获取OAID",context);
        hookMethodgetOAIDFromXiaoMi(clzloader,"获取OAID",context);

        //该wlan mac可能是随机的(pixel 3xl上就是随机)，在接入wifi输入密码界面，勾选显示高级选项，点击“隐私”，即可选择此次连接是使用随机MAC还是使用设备真实wlan MAC
        hookMethodWithOnlyResult("java.net.NetworkInterface",clzloader,"getHardwareAddress","获取WLAN MAC地址",context);
        hookMethodWithOnlyResult("android.net.wifi.WifiInfo",clzloader,"getMacAddress","获取WLAN MAC地址",context);
        hookMethodWithOnlyResult("android.app.ActivityManager",clzloader,"getRunningAppProcesses","读取当前运行应用进程",context);
        hookMethodWithOnlyResult("android.telephony.SmsMessage",clzloader,"getMessageBody","获取短信内容",context);
        hookMethodWithOnlyResult("android.accounts.AccountManager",clzloader,"getAccounts","获取系统账户信息",context);
        hookMethodWithOnlyResult("android.telephony.gsm.SmsMessage",clzloader,"getDisplayMessageBody","获取短信息内容",context);
        hookMethodWithOnlyResult("android.telephony.NeighboringCellInfo",clzloader,"getCid","获取位置信息(获取附近基站id)",context);
        hookMethodWithOnlyResult("android.telephony.gsm.GsmCellLocation",clzloader,"getCid","获取位置信息(获取附近基站id)",context);
        hookMethodWithOnlyResult("android.telephony.gsm.GsmCellLocation",clzloader,"getLac","获取位置信息(获取当前地区区域码)",context);
        hookMethodWithOnlyResult("android.bluetooth.BluetoothAdapter",clzloader,"getAddress","获取蓝牙MAC地址",context);
        hookMethodWithOnlyResult("java.io.File",clzloader,"mkdir","尝试写入SDCard数据(创建)",context);
        //hookMethodWithOnlyResult("android.os.SystemClock",clzloader,"elapsedRealtime","系统开机时间",context);

        hookAllmethodHandleResultAndParam("android.location.LocationManager","getLastKnownLocation","读取精细位置信息",context);
        //该方法有两个重载，但测试过程中发现只调用了connect(SocketAddress, int)
        //hookMethodConstructors("java.net.URI",clzloader,"http连接请求",context);
        //hookAllmethodHandleResultAndParam("android.app.ApplicationPackageManager","getActivityInfo","获取Activity的信息",context);
        hookAllmethodHandleResultAndParam("java.net.Socket","connect","socket连接请求",context);
        hookAllmethodHandleResultAndParam("android.provider.Browser","getAllVisitedUrls","获取所有访问过的链接",context);
        hookAllmethodHandleResultAndParam("android.provider.Browser","getAllBookmarks","获取所有浏览器书签",context);
        hookAllmethodHandleResultAndParam("android.hardware.SensorManager","getSensorList","获取传感器列表",context);
        hookAllmethodHandleResultAndParam("android.media.AudioRecord","startRecording","开始录音",context);
        hookAllmethodHandleResultAndParam("android.hardware.Camera","open","打开摄像头",context);
        hookAllmethodHandleResultAndParam("android.hardware.camera2.CameraManager","openCamera","打开摄像头",context);
        hookAllmethodHandleResultAndParam("android.hardware.Camera","takePicture","拍照",context);
        hookAllmethodHandleResultAndParam("android.app.ActivityManager","getRunningTasks","获取当前运行任务",context);
        hookAllmethodHandleResultAndParam("android.app.ApplicationPackageManager","getPackageInfoAsUser","获取已安装App信息",context);
        hookAllmethodHandleResultAndParam("android.app.ApplicationPackageManager","getInstalledApplicationsAsUser","获取已安装应用列表",context);
        //hookAllmethodHandleResultAndParam("android.app.ApplicationPackageManager","getInstalledPackagesAsUser","获取已安装包名列表",context);
        hookAllmethodHandleResultAndParam("android.content.pm.IPackageManager$Stub$Proxy","getInstalledApplications","获取已安装应用列表",context);
        hookAllmethodHandleResultAndParam("android.content.pm.IPackageManager$Stub$Proxy","getInstalledPackages","获取已安装包名列表",context);
        hookAllmethodHandleResultAndParam("android.content.pm.IPackageManager$Stub$Proxy","queryIntentActivities","获取已安装App信息",context);
        hookAllmethodHandleResultAndParam("androidx.core.app.ActivityCompat","requestPermissions","APP申请权限",context);
        hookAllmethodHandleResultAndParam("android.app.Fragment","requestPermissions","APP申请权限",context);
        hookAllmethodHandleResultAndParam("android.app.Activity","requestPermissions","APP申请权限",context);
        hookAllmethodHandleResultAndParam("android.location.LocationManager","requestLocationUpdates","监视精细行动轨迹",context);
        hookAllmethodHandleResultAndParam("android.telephony.SmsManager","sendDataMessage","发送短信",context);
        hookAllmethodHandleResultAndParam("android.telephony.SmsManager","sendTextMessage","发送短信",context);

        /*
        getDeviceId方法返回的是DEVICEID，android6.0(含)以上都有该方法，但Android10(含)以上无法通过该方法获取DEVICEID，因为需要READ_PRIVILEGED_PHONE_STATE权限，而该权限只能是系统应用才可以获取到。
        若当前设备为CDWA网络，则DEVICEID=MEID，若设备为GSM网络，则DEVICEID=IMEI，若手机支持双卡双待，则返回卡槽1对应的DEVICEID；

        Android 8.0(含)以后不推荐getDeviceId方法,获取IMEI和MEID分别有不同的方法，getMeid返回MEID，getImei返回IMEI，同样Android10(含)以后不能通过getMeid、getImei获取
        */
        hookAllmethodHandleResultAndParam("android.telephony.TelephonyManager","getDeviceId","获取DEVICEID",context);
        hookMethodWithOnlyResult("android.telephony.TelephonyManager",clzloader,"getImei","获取设备唯一标识(IMEI)",context);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            hookMethodWithOnlyResult("android.telephony.TelephonyManager",clzloader,"getMeid","获取MEID",context);
        }

        hookAllmethodHandleResultAndParam("java.lang.Runtime","exec","",context);
        hookAllmethodHandleResultAndParam("android.provider.Settings$NameValueCache","getStringForUser","获取系统设置",context);
        hookAllmethodHandleResultAndParam("android.os.SystemProperties","get","",context);
        hookAllmethodHandleResultAndParam("android.content.ContentResolver","query","",context);
        hookAllmethodHandleResultAndParam("android.net.wifi.WifiInfo","getBSSID","获取WIFI的BSSID",context);
        hookAllmethodHandleResultAndParam("android.net.wifi.WifiInfo","getSSID","获取WIFI的SSID",context);
        //hookAllmethodHandleResultAndParam("android.content.ClipboardManager","addPrimaryClipChangedListener","监视剪贴板",context);
        hookAllmethodHandleResultAndParam("android.os.Build","getSerial","获取设备硬件序列号(SN)",context);
        hookAllmethodHandleResultAndParam("android.telephony.TelephonyManager","getCellLocation","获取位置信息(获取基站位置)",context);
        hookAllmethodHandleResultAndParam("android.telephony.TelephonyManager","getAllCellInfo","获取位置信息(获取基站信息)",context);
        hookAllmethodHandleResultAndParam("android.os.Environment","getExternalStorageDirectory","访问外部存储",context);
        hookAllmethodHandleResultAndParam("android.os.Environment","getExternalStoragePublicDirectory","访问外部存储",context);
        hookAllmethodHandleResultAndParam("android.app.AlarmManager","setImpl","定时启动",context);
        //hookAllmethodHandleResultAndParam("android.app.ContextImpl","bindServiceCommon","关联启动",context);
        hookAllmethodHandleResultAndParam("java.net.SocketOutputStream","socketWrite0","HTTP传输网络数据",context);
        hookAllmethodHandleResultAndParam("com.android.org.conscrypt.NativeCrypto","SSL_write","HTTPS传输网络数据",context);
        hookAllmethodHandleResultAndParam("android.content.ContextWrapper","bindService","启动服务",context);

        hookMethodloadDex(clzloader,"加载插件",context);
        hookOnclickEvent(clzloader,"onClick点击事件",context);
        hookMethodConstructors("dalvik.system.DexClassLoader",clzloader,"动态加载",context);

        hookMethodonStartCommand(clzloader,"自启动",context);



    }

    /**
     * 针对小米手机，对oaid的获取进行hook
     * @param clzloader classloader
     * @param apidesc 获取oaid
     * @param ctx context
     */
    @SuppressLint("PrivateApi")
    private void hookMethodgetOAIDFromXiaoMi(ClassLoader clzloader, String apidesc, Context ctx){
        try {
            XposedHelpers.findAndHookMethod(Class.forName("com.android.id.impl.IdProviderImpl",false,clzloader).getName(),
                    clzloader,
                    "getOAID",
                    Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            super.afterHookedMethod(param);
                            Utils.myLog("com.android.id.impl.IdProviderImpl.getOAID 正在被插件hook");
                            Long timestamp = System.currentTimeMillis();
                            String stack = methodStackTrace("com.android.id.impl.IdProviderImpl.getOAID");
                            String XM_OAID = String.valueOf(param.getResult());
                            JSONObject XM_OAIDInfo = new JSONObject();
                            XM_OAIDInfo.put("OAID",XM_OAID);
                            insertContentProvider(timestamp, apidesc, stack,XM_OAIDInfo.toJSONString(),ctx);
                            Utils.myLog("App服务存在获取OAID行为");
                        }
                    });
        }catch (Throwable e){
            Utils.myErrorlog("hookMethodgetOAIDFromXiaoMi:"+e);
        }
    }

    /**
     * hook loadclass，获取app加载的类及加载类所使用的classloader
     */
    private void hookMethodloadClass(){
        try {
            XposedHelpers.findAndHookMethod(ClassLoader.class,
                    "loadClass",
                    String.class,
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            super.afterHookedMethod(param);
                            if (param == null) {
                                return;
                            }
                            Class<?> cls = (Class<?>) param.getResult();
                            if (cls == null) {
                                return;
                            }
                            isNeedAddClassloader(cls.getClassLoader());
                        }
                    });
        }catch (Throwable e){
            Utils.myErrorlog("hookMethodloadClass:"+e);
        }
    }

    /**
     * 将app所有的classloader保存起来
     * @param classLoader  classloader
     */
    private void isNeedAddClassloader(ClassLoader classLoader) {
        for (ClassLoader loader : AppAllCLassLoaderList) {
            if (loader.hashCode() == classLoader.hashCode()) {
                return;
            }
        }
        AppAllCLassLoaderList.add(classLoader);
        AppAllCLassLoaderStringList.add(classLoader.getClass().getName());
        Utils.myErrorlog("App所有的classloader："+AppAllCLassLoaderStringList.toString());
    }

    @SuppressLint("PrivateApi")
    private void hookMethodBroadcast(ClassLoader clzloader, String apidesc, Context ctx) {
        Utils.myLog("ServiceClassloader in hookMethodBroadcast:"+clzloader);
        try {
            XposedHelpers.findAndHookMethod(Class.forName(
                    "com.android.server.am.BroadcastQueue",false,clzloader).getName(), //Android 9.0(含) 以下，9.0以上没有processNextBroadcast接口
                    clzloader,
                    "processNextBroadcast",
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            super.beforeHookedMethod(param);
                            Utils.myLog("com.android.server.am.BroadcastQueue.processNextBroadcast 正在被插件hook");
                            Long timestamp = System.currentTimeMillis();
                            String desc = apidesc;
                            String stack = methodStackTrace("com.android.server.am.BroadcastQueue.processNextBroadcast");
                            JSONObject BroadcastInfo = new JSONObject();
                            boolean fromMsg = (boolean)param.args[0];
                            BroadcastInfo.put("布尔值",fromMsg);
                            insertContentProvider(timestamp, desc, stack, BroadcastInfo.toJSONString(), ctx);
                            Utils.myLog("App正在"+desc);
                        }
                    });
        }catch (Throwable e){
            Utils.myErrorlog("hookMethodBroadcast:"+e);
        }
    }



    /**
     * hook 某一个特定的方法:service 的onStartCommand方法
     * 原理：通过hook service的startService方法，从参数中获取service实例，过滤掉相同的service后，hook service中的onStartCommand方法
     * @param clzloader clzloader
     */
    @SuppressLint("PrivateApi")
    private void hookMethodonStartCommand(ClassLoader clzloader, String apidesc, Context ctx){
        ArrayList<String> ServiceClassNamelists = new ArrayList<>();
        try {
            XposedHelpers.findAndHookMethod(
                    Class.forName("android.content.ContextWrapper",false,clzloader).getName(),
                    clzloader,
                    "startService",
                    Intent.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            super.beforeHookedMethod(param);
                            Utils.myLog("android.content.ContextWrapper.startService 正在被插件hook");
                            Long timestamp = System.currentTimeMillis();
                            String ServiceClassName = getIntentInfo((Intent)param.args[0]).getJSONObject("cmp").getString("className");

                            if(!ServiceClassNamelists.contains(ServiceClassName)){
                                //Utils.myLog(ServiceClassName);
                                ServiceClassNamelists.add(ServiceClassName);
                                JSONObject ServiceInfo = new JSONObject();
                                String stack = methodStackTrace("android.content.ContextWrapper.startService");
                                ServiceInfo.put("ServiceName",ServiceClassName);
                                insertContentProvider(timestamp, "启动服务", stack,ServiceInfo.toJSONString(),ctx);
                                Utils.myLog("App正在启动服务");

                                final Class<?> ServiceClassClassrf = XposedHelpers.findClass(ServiceClassName,clzloader);
                                XposedBridge.hookAllMethods(ServiceClassClassrf,"onStartCommand",new XC_MethodHook() {
                                    @Override
                                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                        super.afterHookedMethod(param);
                                        Utils.myLog(ServiceClassName+".onStartCommand正在被插件hook");
                                        int result = (Integer)param.getResult();
                                        if(result == 1){
                                            String desc = apidesc;
                                            String stack = methodStackTrace(ServiceClassName+".onStartCommand");
                                            JSONObject AutoStartInfo = new JSONObject();
                                            AutoStartInfo.put("intent",ServiceClassName);
                                            AutoStartInfo.put("result","START_STICKY");
                                            insertContentProvider(timestamp, desc, stack,AutoStartInfo.toJSONString(),ctx);
                                            Utils.myLog("App服务存在自启动行为");
                                        }
                                    }
                                });
                            }
                        }
                    });
        }catch (Throwable e){
            Utils.myErrorlog("hookMethodonStartCommand:"+e);
        }
    }

    /**
     * hook 某一个特定的方法:loadDex
     * @param clzloader clzloader
     */
    private void hookMethodloadDex(ClassLoader clzloader,String apidesc,Context ctx){
        try {
            XposedHelpers.findAndHookMethod("dalvik.system.DexFile",
                    clzloader,
                    "loadDex",
                    String.class,
                    String.class,
                    int.class,
                    new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    Utils.myLog("dalvik.system.DexFile.loadDex 正在被插件hook");
                    Long timestamp = System.currentTimeMillis();
                    String desc = apidesc;
                    String stack = methodStackTrace("dalvik.system.DexFile.loadDex");
                    JSONObject dexPathInfo = new JSONObject();
                    String dexPath = (String) param.args[0];
                    dexPathInfo.put("dexPath",dexPath);
                    insertContentProvider(timestamp, desc, stack, dexPathInfo.toJSONString(), ctx);
                    Utils.myLog("App正在"+desc+dexPath);
                }
            });
        }catch (Throwable e){
            Utils.myErrorlog("hookMethodloadDex:"+e);
        }
    }



    /**
     * hook onClick事件，主要用来判断用户是否点击隐私政策“同意”按钮
     * 实现原理：先通过hook setOnClickListener方法，即button注册点击事件监听器，调用该方法的类因为implements OnClickListener接口，也就会
     * 去重写onClick方法，而setOnClickListener方法参数就是onclick的实现类，当找到了onclick的实现类之后，就可以hook onClick方法了
     * @param clzloader clz
     * @param apidesc apidesc
     * @param ctx ctx
     */
    private void hookOnclickEvent(ClassLoader clzloader,String apidesc,Context ctx){
        try {
            ArrayList<String> clzlists = new ArrayList<>();
            XposedHelpers.findAndHookMethod(View.class ,
                    "setOnClickListener",
                    View.OnClickListener.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if(param.args[0] == null){
                                return;
                            }
                            Class<?> clazz = param.args[0].getClass();
                            String strClazz = clazz.getName();
                            if(!clzlists.contains(strClazz)){ //过滤掉在同一个类中多次注册的场景
                                clzlists.add(strClazz);
                                final Class<?> classrf = XposedHelpers.findClass(strClazz,clzloader);
                                XposedBridge.hookAllMethods(classrf,"onClick",new XC_MethodHook() {
                                    /*这里需要在beforeHookedMethod中执行hook，如果在afterHookedMethod中hook onclick，则逻辑会有问题，比如场景：点击某按钮后获取剪切板，
                                    * 如果在afterHookedMethod，则显示出来的app行为数据是：获取剪切板行为->onclick事件，
                                    * 如果beforeHookedMethod，则显示出来的app行为数据是：onclick事件->取剪切板行为
                                    * 原因分析：onclick中存在剪切板方法同样会被hook，如果使用afterHookedMethod，则onclick需要执行完成后才能被hook，但在执行过程中触发了
                                    * 剪切板方法被hook，所以行为数据是：获取剪切板行为->onclick事件*/
                                    @Override
                                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                        super.beforeHookedMethod(param);
                                        Utils.myLog(strClazz+".onClick正在被插件hook");
                                        Long timestamp = System.currentTimeMillis();
                                        String desc = apidesc;
                                        String stack = methodStackTrace(strClazz+".onClick");
                                        JSONObject buttoninfo = new JSONObject();
                                        View view = (View) param.args[0];
                                        int buttonId= view.getId(); //按钮id
                                        buttoninfo.put("button ID",buttonId);

                                        Utils.myLog("button继承的view类型："+view.getClass());
                                        if(view instanceof TextView){
                                            String buttonLable = ((TextView) view).getText().toString(); //按钮名称
                                            buttoninfo.put("button Lable",buttonLable);
                                            insertContentProvider(timestamp, desc, stack,buttoninfo.toJSONString(),ctx);
                                            Utils.myLog("App正在点击Button："+buttonLable);
                                        }else {
                                            Utils.myLog(strClazz+".onClick正在被插件hook,但未识别view类型");
                                            return;
                                        }
                                    }
                                });
                            }
                        }
                    });
        }catch (Throwable e){
            Utils.myErrorlog("hookOnclickEvent:"+e);
        }

    }



    /**
     * hook 构造器
     * @param clz class
     * @param clzloader classloader
     * @param apidesc api描述
     * @param ctx context
     */
    private void hookMethodConstructors(String clz,ClassLoader clzloader,String apidesc,Context ctx){
        try {
            XposedBridge.hookAllConstructors(XposedHelpers.findClass(clz, clzloader),
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            super.afterHookedMethod(param);
                            Utils.myLog(clz+" 构造器正在被插件hook");
                            long timestamp = System.currentTimeMillis();
                            String desc = apidesc;
                            String stack = methodStackTrace(clz);
                            switch (clz){
                                case "java.net.URI":
                                    String UrlPath = (String) param.args[0];
                                    JSONObject UrlPathInfo = new JSONObject();
                                    UrlPathInfo.put("url",UrlPath);
                                    insertContentProvider(timestamp, desc, stack, UrlPathInfo.toJSONString(), ctx);
                                    Utils.myLog("App正在"+desc+UrlPath);
                                    break;
                                case "dalvik.system.DexClassLoader":
                                    String jarPath = (String) param.args[0];
                                    if(jarPath.contains("edxp")){
                                        Utils.myLog("动态加载了edxposed的jar包");
                                        return;
                                    }
                                    JSONObject jarPathInfo = new JSONObject();
                                    jarPathInfo.put("jarPath",jarPath);
                                    insertContentProvider(timestamp, desc, stack, jarPathInfo.toJSONString(), ctx);
                                    Utils.myLog("App正在"+desc+jarPath);
                                    break;
                                default:
                                    break;
                            }
                        }
                    });
        }catch (Throwable e){
            Utils.myErrorlog("hookMethodConstructors:"+e);
        }
    }
    /**
     * hook某个方法所有重载，并且处理返回值和参数
     * @param clz  被hook的类名
     * @param method 被hook的方法名
     * @param apidesc 被hook方的描述
     * @param ctx context
     */
    private void hookAllmethodHandleResultAndParam(String clz,String method,String apidesc,Context ctx){
        try {
            XposedBridge.hookAllMethods(Class.forName(clz), method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    Utils.myLog(clz+"."+method+" 正在被插件hook");
                    long timestamp = System.currentTimeMillis();
                    String desc = apidesc;
                    String stack = methodStackTrace(clz+"."+method);
                    switch (method) {
                        case "bindService":
                            JSONObject ServiceIntent = getIntentInfo((Intent) param.args[0]);
                            //insertContentProvider(timestamp, desc, stack, ServiceIntent.toJSONString(), ctx);
                            Utils.myLog("App正在"+desc+":"+ServiceIntent.toJSONString());
                            //检测谷歌广告id(GAID)是否被获取
                            if("com.google.android.gms.ads.identifier.service.START".equals(ServiceIntent.getString("act")) && "com.google.android.gms".equals(ServiceIntent.getString("packageName"))){
                                insertContentProvider(timestamp, "获取GAID", stack, ServiceIntent.toJSONString(), ctx);
                                Utils.myLog("App正在获取GAID");
                            }
                            break;
                        case "socketWrite0":
                            String SocketDataBase64Str;
                            String SocketDataContent; // http传输的数据内容
                            String SocketDataTrasMethod; // https传输数据的方法

                            byte[] SocketDataByte = (byte[]) param.args[1];
                            int SocketDataOff = (int) param.args[2]; //数据偏移量
                            int SocketDataLength = (int) param.args[3]; //数据长度
                            SocketDataContent = new String(SocketDataByte,SocketDataOff,SocketDataLength);
                            Utils.myLog("App正在通过HTTP协议传输数据,数据偏移量：" + SocketDataOff +" 数据长度：" + SocketDataLength + " 数据内容是:\n"+SocketDataContent);

                            if(SocketDataContent.length() >= 8){
                                SocketDataTrasMethod = SocketDataContent.substring(0,8); //截取前8个字符，通过contain来判断数据传输method
                                if(SocketDataTrasMethod.contains("DELETE")|| SocketDataTrasMethod.contains("HEAD")|| SocketDataTrasMethod.contains("OPTIONS")|| SocketDataTrasMethod.contains("PUT")|| SocketDataTrasMethod.contains("POST") || SocketDataTrasMethod.contains("GET")){
                                    SocketDataBase64Str = Base64.encodeToString(SocketDataContent.getBytes(),Base64.NO_WRAP);
                                    insertContentProvider(timestamp, desc, stack, SocketDataBase64Str, ctx);
                                }
                            }
                            break;

                        case "SSL_write":
                            String SSLDataBase64Str; //通过base64编码写入到数据库中
                            String SSLDataContent; // https传输的数据内容
                            String SSLDataTrasMethod; // https传输数据的方法
                            byte[] SSLDataByte;
                            int SSLDataOff;
                            int SSLDataLength;
                            if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.O){ //android9(不含)以下，SSL_write方法参数不同
                                SSLDataByte = (byte[]) param.args[3]; //数据内容
                                SSLDataOff = (int) param.args[4]; //数据偏移量
                                SSLDataLength = (int) param.args[5]; //数据长度
                            }else {
                                SSLDataByte = (byte[]) param.args[4]; //数据内容
                                SSLDataOff = (int) param.args[5]; //数据偏移量
                                SSLDataLength = (int) param.args[6]; //数据长度
                            }

                            SSLDataContent = new String(SSLDataByte,SSLDataOff,SSLDataLength);
                            Utils.myLog("App正在通过HTTPS协议传输数据,数据偏移量：" + SSLDataOff +" 数据长度：" + SSLDataLength + " 数据内容是:\n"+SSLDataContent);

                            if(SSLDataContent.length()>=8){
                                SSLDataTrasMethod = SSLDataContent.substring(0,8); //截取前8个字符，通过contain来判断数据传输method
                                if(SSLDataTrasMethod.contains("DELETE")|| SSLDataTrasMethod.contains("HEAD")|| SSLDataTrasMethod.contains("OPTIONS")|| SSLDataTrasMethod.contains("PUT")|| SSLDataTrasMethod.contains("POST") || SSLDataTrasMethod.contains("GET")){
                                    SSLDataBase64Str = Base64.encodeToString(SSLDataContent.getBytes(),Base64.NO_WRAP);
                                    insertContentProvider(timestamp, desc, stack, SSLDataBase64Str, ctx);
                                }
                            }
                            break;
                        case "getSensorList":
                            String SensorType = String.valueOf(param.args[0]);//该接口参数本来是int类型，这里转成String类型，方便数据分析端从JSON中分析数据
                            JSONObject SensorTypePara = new JSONObject();
                            SensorTypePara.put("SensorType",SensorType);
                            insertContentProvider(timestamp, desc, stack, SensorTypePara.toJSONString(), ctx);
                            Utils.myLog("App正在"+desc);
                            break;
                        case "getActivityInfo":
                            JSONObject ActivityInfo = new JSONObject();
                            ComponentName ComponentName  = (ComponentName) param.args[0];
                            String ComponentInfo = ComponentName.getPackageName()+"/"+ComponentName.getClassName();
                            int flags = (int) param.args[1];
                            ActivityInfo.put("ComponentInfo",ComponentInfo);
                            ActivityInfo.put("flags",flags);
                            insertContentProvider(timestamp, desc, stack, ActivityInfo.toJSONString(), ctx);
                            //Utils.myErrorlog(ActivityInfo.toJSONString());
                            Utils.myLog("App正在"+desc);
                            break;
                        case "bindServiceCommon":
                            Intent intentService = (Intent) param.args[0];
                            insertContentProvider(timestamp, desc, stack, getIntentInfo(intentService).toJSONString(), ctx);
                            Utils.myLog("App正在"+desc+":"+getIntentInfo(intentService).toJSONString());
                            break;
                        case "setImpl":
                            JSONObject timedJob = new JSONObject();
                            int alarmType = (int) param.args[0]; //定时任务类型
                            if(alarmType == 0){
                                timedJob.put("定时任务类型","RTC_WAKEUP");
                            }else if(alarmType == 1){
                                timedJob.put("定时任务类型","RTC");
                            }else if(alarmType == 2){
                                timedJob.put("定时任务类型","ELAPSED_REALTIME_WAKEUP");
                            }else {
                                timedJob.put("定时任务类型","ELAPSED_REALTIME");
                            }
                            long triggerAtMillis = (long) param.args[2]; //首次触发定时任务时间
                            long intervalMillis = (long) param.args[3];  //重复定时任务时间间隔
                            if(intervalMillis == 0){
                                timedJob.put("执行周期","单次");
                            }else {
                                timedJob.put("执行周期",intervalMillis);
                            }
                            //PendingIntent operation = (PendingIntent) param.args[5];
                            //timedJob.put("PendingIntent",operation.toString());
                            timedJob.put("首次触发时间",Utils.dataformat(triggerAtMillis));
                            insertContentProvider(timestamp,desc,stack,timedJob.toJSONString(),ctx);
                            Utils.myLog("App正在设置"+desc);
                            break;
                        case "getPackageInfoAsUser":
                            JSONObject appInfoObj = new JSONObject();
                            String packageName = param.args[0].toString();
                            int flag = (int) param.args[1];
                            if(!packageName.contains(targetAppPackageName)){ //规则是获取已安装App信息，这里App应该是指除了自己之外的，所以需要排除/过滤掉自身App
                                appInfoObj.put("packageName",packageName);
                                appInfoObj.put("flag",flag);
                                insertContentProvider(timestamp,desc,stack,appInfoObj.toJSONString(),ctx);
                                Utils.myLog("App正在"+desc+":"+packageName);
                            }
                            break;
                        case "queryIntentActivities":
                            Intent intent = (Intent) param.args[0];
                            insertContentProvider(timestamp, desc, stack, getIntentInfo(intent).toJSONString(), ctx);
                            Utils.myLog("App正在"+desc+":"+getIntentInfo(intent).toJSONString());
                            break;
                        case "doLoad":
                        case "nativeLoad":
                            JSONObject soFileObj = new JSONObject();
                            String sofile = (String) param.args[0];
                            soFileObj.put("sofile",sofile);
                            insertContentProvider(timestamp, desc, stack, soFileObj.toJSONString(), ctx);
                            Utils.myLog("App正在"+desc);
                            break;
                        case "requestPermissions":
                            if("androidx.core.app.ActivityCompat".equals(clz)){
                                String[] acPermissions = (String[]) param.args[1];
                                StringBuilder ac_pers = new StringBuilder();
                                for(int n=0;n<acPermissions.length;n++){
                                    //PerM1New1为标识符，生成excel文件时候，替换成换行符
                                    ac_pers.append(acPermissions[n]).append("PerM1New1");
                                }
                                insertContentProvider(timestamp, desc, stack, ac_pers.toString(), ctx);
                                Utils.myLog("App正在"+desc);
                                break;
                            }else if("android.app.Fragment".equals(clz) || "android.app.Activity".equals(clz)) {
                                String[] ot_permissions = (String[]) param.args[0];
                                StringBuilder otPermissions = new StringBuilder();
                                for(int n=0;n<ot_permissions.length;n++){
                                    otPermissions.append(ot_permissions[n]).append("PerM1New1");
                                }
                                insertContentProvider(timestamp, desc, stack, otPermissions.toString(), ctx);
                                Utils.myLog("App正在"+desc);
                                break;
                            }
                            break;
                        case "getExternalStorageDirectory"://获取默认返回值
                        case "getExternalStoragePublicDirectory":
                        case "sendDataMessage":
                        case "sendTextMessage":
                        case "getDeviceId":
                        case "getProcessName":
                        case "getBSSID":
                        case "getSSID":
                        case "getSerial":
                            JSONObject resultObj = new JSONObject();
                            String resultInfo =  String.valueOf(param.getResult());
                            resultObj.put("result",resultInfo);
                            insertContentProvider(timestamp, desc, stack, resultObj.toJSONString(), ctx);
                            Utils.myLog("App正在"+desc);
                            break;
                        case "connect": //获取socket链接，分割出socket套接字信息，参数args[0]返回原始内容：cdncs.ykt.cbern.com.cn/125.74.1.41:443
                            //String connect_detail = param.args[0].toString().split("/")[1];
                            String connectDetail = param.args[0].toString();
                            insertContentProvider(timestamp, desc, stack, connectDetail, ctx);
                            Utils.myLog("App正在"+desc);
                            break;
                        case "exec":  //根据方法参数自定义desc
                            String cmdstr = param.args[0].toString();
                            if(cmdstr.contains("ifconfig")){
                                desc = "获取网卡信息";
                            }
                            if(cmdstr.contains("pm") && cmdstr.contains("list") && cmdstr.contains("packages")){
                                desc = "获取已安装应用列表";
                            }
                            if(cmdstr.contains("settings")&& cmdstr.contains("get")&& cmdstr.contains("secure")){
                                desc = "获取Android ID";
                            }
                            if(cmdstr.contains("cat")&&cmdstr.contains("/sys/class/net/")&&cmdstr.contains("address")){
                                desc = "获取网卡MAC地址";
                            }
                            if(cmdstr.contains("getprop") && cmdstr.contains("ro.serialno")){
                                desc = "获取设备序列号(SN)";
                            }
                            if(!isBlank(desc)){
                                JSONObject cmdObjection = new JSONObject();
                                cmdObjection.put("cmd",cmdstr);
                                insertContentProvider(timestamp, desc, stack, cmdObjection.toJSONString(), ctx);
                                Utils.myLog("App正在"+desc);
                            }
                            break;
                        case "get": //根据方法参数自定义desc
                            String getArg1 = param.args[0].toString();
                            if(getArg1.contains("ro.serialno")){
                                JSONObject snObj = new JSONObject();
                                desc = "获取设备序列号(SN)";
                                String serialno = (String) param.getResult();
                                snObj.put("SN",serialno);
                                insertContentProvider(timestamp, desc, stack, snObj.toJSONString(), ctx);
                                Utils.myLog("App正在"+desc);
                            }
                            if(getArg1.contains("gsm.sim.operator.alpha")||getArg1.contains("gsm.sim.operator.numeric")){
                                JSONObject operationObj = new JSONObject();
                                desc = "获取设备运营商";
                                String operator = (String) param.getResult();
                                operationObj.put("运营商",operator);
                                insertContentProvider(timestamp, desc, stack, operationObj.toJSONString(), ctx);
                                Utils.myLog("App正在"+desc);
                            }
                            break;
                        case "query": //根据方法参数自定义desc
                            String queryArg1 = param.args[0].toString();
                            if(queryArg1.contains("content://com.android.contacts/")){
                                desc = "获取通讯录数据";
                                String contacts = (String) param.getResult();
                                insertContentProvider(timestamp, desc, stack, contacts, ctx);
                                Utils.myLog("App正在"+desc);
                            }else if(queryArg1.contains("content://call_log/")){
                                desc = "获取通话记录";
                                String callLog = (String) param.getResult();
                                insertContentProvider(timestamp, desc, stack, callLog, ctx);
                                Utils.myLog("App正在"+desc);
                            }else if(queryArg1.contains("content://sms/")){
                                desc = "获取短信记录";
                                String smsLog = (String) param.getResult();
                                insertContentProvider(timestamp, desc, stack, smsLog, ctx);
                                Utils.myLog("App正在"+desc);
                            }else {
                                break;
                            }
                            break;
                        case "getLastKnownLocation": //上次位置信息
                            JSONObject locationInfo = new JSONObject();
                            String locLongitude = null;
                            String locLatitude = null;
                            String provider = (String) param.args[0];
                            if(!Utils.isEmpty(param.getResult())){
                                Location location = (Location) param.getResult();
                                locLongitude =  String.valueOf(location.getLongitude());
                                locLatitude = String.valueOf(location.getLatitude());
                            }
                            locationInfo.put("location provider",provider);
                            locationInfo.put("longitude",locLongitude);
                            locationInfo.put("latitude",locLatitude);
                            insertContentProvider(timestamp,desc,stack,locationInfo.toJSONString(),ctx);
                            Utils.myLog("App正在"+desc);
                            break;
                        case "getStringForUser": //根据方法参数自定义desc
                            String getStringForUserArg2 = param.args[1].toString();
                            if(getStringForUserArg2.contains("android_id")){
                                JSONObject androidIdObj = new JSONObject();
                                desc = "获取Android ID";
                                String androidId = String.valueOf(param.getResult()) ;
                                androidIdObj.put("Android ID",androidId);
                                insertContentProvider(timestamp, desc, stack, androidIdObj.toJSONString(), ctx);
                                Utils.myLog("App正在"+desc);
                            }else if(getStringForUserArg2.contains("bluetooth_name")){
                                JSONObject blueToothNameObj = new JSONObject();
                                desc = "获取蓝牙名称";
                                String blueToothName = String.valueOf(param.getResult());
                                blueToothNameObj.put("Bluetooth Name",blueToothName);
                                insertContentProvider(timestamp, desc, stack, blueToothNameObj.toJSONString(), ctx);
                                Utils.myLog("App正在"+desc);
                            }else if(getStringForUserArg2.contains("device_name")){
                                JSONObject deviceNameObj = new JSONObject();
                                desc = "获取设备名称";
                                String deviceName = String.valueOf(param.getResult());
                                deviceNameObj.put("Device Name",deviceName);
                                insertContentProvider(timestamp, desc, stack, deviceNameObj.toJSONString(), ctx);
                                Utils.myLog("App正在"+desc);
                            }else {
                                break;
                                /*JSONObject systemSettingObj = new JSONObject();
                                String systemSetting = String.valueOf(param.getResult());
                                systemSettingObj.put("系统设置",systemSetting);
                                insertContentProvider(timestamp, desc, stack, systemSettingObj.toJSONString(), ctx);
                                Utils.myLog("App正在"+desc);*/
                            }
                            break;
                        default:
                            insertContentProvider(timestamp, desc, stack, DO_NOT_NEED_RETURN, ctx);
                            Utils.myLog("App正在"+desc);
                            break;
                    }
                }
            });
        } catch (Throwable e) {
            Utils.myErrorlog("hookAllmethodHandleResultAndParam:"+e);
        }
    }
    /**
     * hook某个无参数方法，且只处理这个方法的返回值
     * @param clz 被hook的类名
     * @param clzloader ClassLoader
     * @param method 被hook的方法名
     * @param apidesc 被hook方的描述
     */
    private void hookMethodWithOnlyResult(String clz,ClassLoader clzloader,String method,String apidesc,Context ctx){
        try {
            XposedHelpers.findAndHookMethod(Class.forName(clz,false,clzloader).getName(),clzloader,method,new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    Utils.myLog(clz+"."+method+" 正在被插件hook");
                    Long timestamp = System.currentTimeMillis();
                    String desc = apidesc;
                    String stack = methodStackTrace(clz+"."+method);
                    switch (method) {
                        case "elapsedRealtime":
                            long elapsedMillis = (long) param.getResult();
                            long bootTime = System.currentTimeMillis() - elapsedMillis;
                            insertContentProvider(timestamp,desc,stack, Utils.dataformat(bootTime),ctx);
                            Utils.myLog("App正在"+desc);
                            break;
                        case "mkdir": //小米应用市场关注的是从方法mkdirs调用mkdir
                            //Utils.myErrorlog(stack);
                            if(stack.contains("mkdirs")){
                                Field[] fields = param.thisObject.getClass().getDeclaredFields();
                                for (Field field : fields) {
                                    field.setAccessible(true);
                                    //Utils.myLog(field.getName()+":"+ field.get(param.thisObject));
                                    if(field.getName().equals("path")){
                                        insertContentProvider(timestamp,desc,stack, String.valueOf(field.get(param.thisObject)),ctx);
                                        Utils.myLog("App正在"+desc);
                                        break;
                                    }
                                }
                            }
                            break;
                        case "getId":
                            JSONObject GAIDObj = new JSONObject();
                            String GAIDInfo = String.valueOf(param.getResult());
                            GAIDObj.put("GAID",GAIDInfo);
                            insertContentProvider(timestamp,desc,stack,GAIDObj.toJSONString(),ctx);
                            Utils.myLog("App正在"+desc);
                            break;
                        case "getOAID":
                            JSONObject OAIDObj = new JSONObject();
                            String OAIDInfo = String.valueOf(param.getResult());
                            OAIDObj.put("OAID",OAIDInfo);
                            insertContentProvider(timestamp,desc,stack,OAIDObj.toJSONString(),ctx);
                            Utils.myLog("App正在"+desc);
                            break;
                        case "getPrimaryClip":
                            String clipstr = null; //剪切板内容变量初始化
                            JSONObject clipItem = new JSONObject();
                            JSONObject clipContent = new JSONObject(); //json变量，格式化封装剪切板内容
                            ClipData data = (ClipData)param.getResult();
                            if(data == null || data.getItemCount()<=0){ //如果剪切板中无内容
                                clipstr = String.valueOf(clipstr);
                                clipContent.put("剪切板内容", clipstr);

                            }else {
                                for(int c=0; c < data.getItemCount(); c++){ //循环读取每个item
                                    clipstr = String.valueOf(data.getItemAt(c).getText());
                                    clipItem.put("Item["+c+"]",URLDecoder.decode(clipstr, "utf-8"));
                                }
                                clipContent.put("剪切板内容", clipItem);
                            }
                            insertContentProvider(timestamp,desc,stack,clipContent.toJSONString(),ctx);
                            Utils.myLog("App正在"+desc);
                            break;
                        case "getHardwareAddress"://将返回mac地址格式化
                            JSONObject hardwareAddressObj = new JSONObject();
                            String hardwareAddressInfo = null;
                            if(param.getResult() != null){
                                hardwareAddressInfo = Utils.Bytes2Str((byte[]) param.getResult());
                            }
                            hardwareAddressObj.put("WLAN MAC",hardwareAddressInfo);
                            insertContentProvider(timestamp,desc,stack,hardwareAddressObj.toJSONString(),ctx);
                            Utils.myLog("App正在"+desc);
                            break;
                        case "getIpAddress"://IP地址格式化
                            String deviceIp = Utils.convertIP((Integer) param.getResult());
                            if(deviceIp.contains("192.168")){
                                JSONObject ipInfo = new JSONObject();
                                ipInfo.put("IP",deviceIp);
                                insertContentProvider(timestamp,desc,stack,ipInfo.toJSONString(),ctx);
                                Utils.myLog("App正在"+desc);
                            }
                            break;
                        case "getInetAddresses":
                            insertContentProvider(timestamp,desc,stack,DO_NOT_NEED_RETURN,ctx);
                            Utils.myLog("App正在"+desc);
                            break;
                        case "show"://只记录一次，第一次为隐私政策弹框
                            if(showOnceBool){
                                showOnceBool = false;
                                /*if(queryContentProvider(ctx) > 0 ){
                                    break;
                                }*/
                                insertContentProvider(timestamp,desc,stack,DO_NOT_NEED_RETURN,ctx);
                                Utils.myLog("App正在"+desc);
                            }
                            break;
                        case "getRunningAppProcesses"://不需要返回值
                        case "getAllVisitedUrls":
                        case "getAllBookmarks":
                            insertContentProvider(timestamp,desc,stack,DO_NOT_NEED_RETURN,ctx);
                            Utils.myLog("App正在"+desc);
                            break;
                        default://默认返回值，不处理
                            JSONObject defaultResult = new JSONObject();
                            String dRresult = String.valueOf(param.getResult());
                            defaultResult.put("result",dRresult);
                            insertContentProvider(timestamp,desc,stack,defaultResult.toJSONString(),ctx);
                            Utils.myLog("App正在"+desc);
                            break;
                    }
                }
            });
        } catch (Throwable e){
            Utils.myErrorlog("hookMethodWithOnlyResult:"+e);
        }

    }
    /**
     * @desc 打印方法堆栈
     */
    private String methodStackTrace(String clzAndmethod) {
        StringBuilder methodStack = new StringBuilder();
        methodStack.append("at ").append(clzAndmethod).append("<--");
        Throwable ex = new Throwable();
        StackTraceElement[] stackElements = ex.getStackTrace();
        for (int i = 0; i < stackElements.length; i++) {
            StackTraceElement element = stackElements[i];
            String str = "at " + element.getClassName() + "." + element.getMethodName() + "(" + element.getFileName() + ":" + element.getLineNumber() + ")"+"<--";
            methodStack.append(str);
        }
        return StringUtils.stripEnd(methodStack.toString(),"<--");
    }

    /**
     * 从content provider 数据库中读取目标包名
     * @param  ctx context
     * @return 包名
     */
    private String getTargetAppFromContentProvider(Context ctx){
        String targetPackageName;
        try {
            Cursor cursor = ctx.getContentResolver().query(MyProvider.CONTENT_URI_PACKAGENAME,new String[]{"PackageName"},null,null,null);
            if(cursor != null){
                if(cursor.moveToFirst()){
                    cursor.moveToFirst();
                    targetPackageName =cursor.getString(0);
                    cursor.close();
                    Utils.myLog("从package表中取到包名:"+targetPackageName);
                    return targetPackageName;
                }else {
                    Utils.myLog("从package表中没有取到目标包名.");
                }
            }
            Utils.myErrorlog("cursor is null.");
        }catch (Throwable e){
            Utils.myErrorlog("获取Target App异常："+e.toString());
        }
        return null;
    }


    /**
     * 向content provider中写入classloadr
     * @param ServiceClassloader 系统启动加载android包的classloadr
     * @param ctx context
     */
    private void insertServiceClassloaderToProvider(ClassLoader ServiceClassloader ,Context ctx){
        try {
            ContentValues values = new ContentValues();
            values.put("ServiceClassloader", String.valueOf(ServiceClassloader));
            ctx.getContentResolver().insert(MyProvider.CONTENT_URI_ServiceClassloader, values);
        }catch (Throwable e){
            Utils.myErrorlog(e.toString());
        }
    }

    private int queryContentProvider(Context ctx){
        Cursor cursor = null;
        try {
            cursor = ctx.getContentResolver().query(MyProvider.CONTENT_URI_API_INFO,// Uri
                    new String[]{"apidesc"},
                    "apidesc='弹出隐私政策授权框'",
                    null,
                    null
            );
            return cursor.getCount();
        }catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return -3;
    }

    /**
     * 将hook数据插入content provider数据库
     * @param timestamp  时间戳
     * @param apidesc 方法描述
     * @param stack 堆栈信息
     * @param info 默认或者经过处理的参数值或返回值，根据需要
     * @param ctx context
     */
    private void insertContentProvider(Long timestamp,String apidesc,String stack,String info,Context ctx){
        try {
            ContentValues values = new ContentValues();
            values.put("timestamp", timestamp);
            values.put("apidesc",apidesc);
            values.put("stack",stack);
            values.put("detail",info);
            ctx.getContentResolver().insert(MyProvider.CONTENT_URI_API_INFO, values);
        }catch (Throwable e){
            Utils.myErrorlog("insertContentProvider异常："+e.toString());
        }
    }



    /**
     * 获取intent信息
     * @param intent intent
     * @return JSON
     */
    private JSONObject getIntentInfo(Intent intent){
        JSONObject intentInfo = new JSONObject();
        if(intent.getAction() != null){
            intentInfo.put("act",intent.getAction());
        }
        if(intent.getCategories()!= null){
            intentInfo.put("cat",intent.getCategories());
        }
        if(intent.getPackage()!= null){
            intentInfo.put("packageName",intent.getPackage());
        }
        if(intent.getData() != null){
            intentInfo.put("data",intent.getData());
        }
        if(intent.getComponent() != null){
            intentInfo.put("cmp",intent.getComponent());
        }
        if(intent.getExtras() != null){
            Set<String> keySet  = intent.getExtras().keySet();
            for(String key:keySet){
                Object value = intent.getExtras().get(key);
                intentInfo.put(key,value.toString());
            }
        }
        return intentInfo;
    }
    private static boolean isBlank(String str) {
        return (str == null || str.trim().isEmpty());
    }

    /**
     * 向CP中写入App启动信息
     * @param ctx ctx
     */
    private void appStartInfo(Context ctx,String appPackageName,String appProcessName){
        Long timestamp = System.currentTimeMillis();
        JSONObject appStartInfo = new JSONObject();
        appStartInfo.put("PackageName",appPackageName);
        appStartInfo.put("processName",appProcessName);
        insertContentProvider(timestamp, "App进程启动", "-", appStartInfo.toJSONString(), ctx);
    }



}
