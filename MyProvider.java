package com.tshare.AppPrivacyCheckTool;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;

import java.util.HashMap;

/**
 * @author 小学生
 */
public class MyProvider extends ContentProvider {

    static final String AUTHORITY  = "com.tshare.AppPrivacyCheckTool";

    /**
     * content provide uri
     */
    static final String URL_API_INFO = "content://" + AUTHORITY + "/ApiInvokeDetails";
    static final String URL_PACKAGENAME = "content://" + AUTHORITY + "/PackageName";
    static final String URL_ServiceClassloader = "content://" + AUTHORITY + "/ServiceClassloader";


    /**
     * 将provide uri 解析成 Uri 对象
     */
    static final Uri CONTENT_URI_API_INFO = Uri.parse(URL_API_INFO);
    static final Uri CONTENT_URI_PACKAGENAME = Uri.parse(URL_PACKAGENAME);
    static final Uri CONTENT_URI_ServiceClassloader = Uri.parse(URL_ServiceClassloader);

    /**
     * 初始化匹配码
     */
    static final int URI_CODE_API_INFO = 1;
    static final int URI_CODE_PACKAGENAME = 2;
    static final int URI_CODE_ServiceClassloader = 3;

    /**
     * 静态的UriMatcher
     */
    static final UriMatcher URI_MATCHER;
    private static HashMap<String, String> values;
    /**
     * 声明数据库
     */
    private SQLiteDatabase db;

    //当MATCHER调用match()方法时，会进行匹配，并返回相应的自定义匹配码，根据匹配码进行操作
    static {
        URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH); // 默认的规则是不匹配的
        URI_MATCHER.addURI(AUTHORITY, "ApiInvokeDetails", URI_CODE_API_INFO);
        URI_MATCHER.addURI(AUTHORITY, "ApiInvokeDetails/*", URI_CODE_API_INFO);
        URI_MATCHER.addURI(AUTHORITY, "PackageName", URI_CODE_PACKAGENAME);
        URI_MATCHER.addURI(AUTHORITY, "PackageName/*", URI_CODE_PACKAGENAME);
        URI_MATCHER.addURI(AUTHORITY, "ServiceClassloader", URI_CODE_ServiceClassloader);
        URI_MATCHER.addURI(AUTHORITY, "ServiceClassloader/*", URI_CODE_ServiceClassloader);
    }

    /**
     *  当ContentProvider第一次被调用的时候会调用onCreate方法，一般在这里进行数据库的初始化
     * 当指定数据库名的数据库不存在时，创建新的数据库，当存在时，打开已有数据库
     */
    @Override
    public boolean onCreate() {
        // TODO Auto-generated method stub
        DatabaseHelper dbHelper = new DatabaseHelper(getContext());
        db = dbHelper.getWritableDatabase();
        if (db != null) {
            return true;
        }
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,String[] selectionArgs, String sortOrder) {
        SQLiteQueryBuilder qb =  new SQLiteQueryBuilder();
        switch (URI_MATCHER.match(uri)) {
            case URI_CODE_ServiceClassloader:
                qb.setTables(DatabaseHelper.TABLE_SCL);
                qb.setProjectionMap(values);
                break;
            case URI_CODE_PACKAGENAME:
                qb.setTables(DatabaseHelper.TABLE_PACKAGENAME);
                qb.setProjectionMap(values);
                break;
            case URI_CODE_API_INFO:
                qb.setTables(DatabaseHelper.TABLE_API_INFO);
                qb.setProjectionMap(values);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI by Tshare -- " + uri);
        }

        //sortOrder即为排序方式，null表示默认返回的可能为无序的
        /*if (sortOrder == null || sortOrder.equals("")) {
            sortOrder = "timestamp";
        }*/
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    /**
     * @param uri 指定表或者行的Uri
     * @param selection 删除的条件
     * @param selectionArgs selection中占位符的值
     * @return 返回删除的某行的行号
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        int deletedRows;
        switch (URI_MATCHER.match(uri)) {
            case URI_CODE_API_INFO:
                deletedRows  = db.delete(DatabaseHelper.TABLE_API_INFO, selection, selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return deletedRows ;
    }

    /**
     * 返回传入Uri的MIME类型
     * @param uri Uri
     * @return 返回数据到MIME类型
     */
    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        switch (URI_MATCHER.match(uri)) {
            case URI_CODE_API_INFO:
                return "vnd.android.cursor.dir/u";

            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
    }

    /**
     * @param uri 指定表或者行的Uri
     * @param values 包装了要插入数据的ContentValues
     * @return 用于表示新纪录的Uri
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO Auto-generated method stub
        switch (URI_MATCHER.match(uri)) {
            case URI_CODE_API_INFO:
                long rowID = db.insert(DatabaseHelper.TABLE_API_INFO, "", values);
                if (rowID > 0) {
                    Uri new_uri = ContentUris.withAppendedId(CONTENT_URI_API_INFO, rowID);
                    getContext().getContentResolver().notifyChange(new_uri, null);
                    return new_uri;
                }
            case URI_CODE_PACKAGENAME:
                db.delete(DatabaseHelper.TABLE_PACKAGENAME,null,null);
                long row_ID = db.insert(DatabaseHelper.TABLE_PACKAGENAME, "", values);
                if (row_ID > 0) {
                    Uri new_uri = ContentUris.withAppendedId(CONTENT_URI_PACKAGENAME, row_ID);
                    getContext().getContentResolver().notifyChange(new_uri, null);
                    return new_uri;
                }
            case URI_CODE_ServiceClassloader:
                db.delete(DatabaseHelper.TABLE_SCL,null,null);
                long ROW_SCL_ID = db.insert(DatabaseHelper.TABLE_SCL, "", values);
                if (ROW_SCL_ID > 0) {
                    Uri new_uri = ContentUris.withAppendedId(CONTENT_URI_ServiceClassloader, ROW_SCL_ID);
                    getContext().getContentResolver().notifyChange(new_uri, null);
                    return new_uri;
                }
            default:
                throw new SQLException("Failed to add a record into " + uri);
        }
    }


    @Override
    public int update(Uri uri, ContentValues values, String selection,String[] selectionArgs) {
        // TODO Auto-generated method stub
        int count;
        switch (URI_MATCHER.match(uri)) {
            case URI_CODE_API_INFO:
                count = db.update(DatabaseHelper.TABLE_API_INFO, values, selection, selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return count;}
}
