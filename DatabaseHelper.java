package com.tshare.AppPrivacyCheckTool;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * @author 小学生
 */
public class DatabaseHelper extends SQLiteOpenHelper {
    /**
     *  数据库名
     */
    static final String DATABASE_NAME = "mydb";
    /**
     * 数据库版本号
     */
    static final int DATABASE_VERSION = 1;
    /**
     * 表名
     */
    static final String TABLE_API_INFO = "ApiInfo";
    static final String TABLE_PACKAGENAME = "PackageName";
    static final String TABLE_SCL = "ServiceClassloader";


    /**
     * 创建表
     */
    static final String CREATE_DB_TABLE_API_INFO = " CREATE TABLE IF NOT EXISTS " + TABLE_API_INFO + " (id INTEGER PRIMARY KEY AUTOINCREMENT,timestamp TEXT ,apidesc TEXT ,detail TEXT ,stack TEXT );";
    static final String CREATE_DB_TABLE_PACKAGENAME = " CREATE TABLE IF NOT EXISTS " + TABLE_PACKAGENAME + " (id INTEGER PRIMARY KEY AUTOINCREMENT,PackageName TEXT );";
    static final String CREATE_DB_TABLE_SCL = " CREATE TABLE IF NOT EXISTS " + TABLE_SCL + " (id INTEGER PRIMARY KEY AUTOINCREMENT,ServiceClassloader TEXT );";

    /**
     * 写入初始化数据
     */
    static final String INSERT_INIT_DATA = "INSERT INTO "+TABLE_PACKAGENAME+" (PackageName) VALUES ("+"\"com.xxxxx.AntiAppScanner\""+");";

    /**
     * @param context 构造器
     */
    DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * 数据库创建时回调方法
     * @param db db
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_DB_TABLE_PACKAGENAME);
        db.execSQL(INSERT_INIT_DATA);
        db.execSQL(CREATE_DB_TABLE_API_INFO);
        db.execSQL(CREATE_DB_TABLE_SCL);
    }

    /**
     * 数据库版本更新的回调方法
     * @param db db
     * @param oldVersion  oldVersion
     * @param newVersion newVersion
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_API_INFO);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PACKAGENAME);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SCL);
        onCreate(db);
    }
}
