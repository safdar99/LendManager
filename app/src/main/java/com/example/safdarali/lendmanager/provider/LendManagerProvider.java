package com.example.safdarali.lendmanager.provider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.UnsupportedSchemeException;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.example.safdarali.lendmanager.data.Friend;
import com.example.safdarali.lendmanager.data.Transaction;

public class LendManagerProvider extends ContentProvider {

    private static final int MY_ACCOUNT = 100;
    private static final int FRIENDS = 200;
    private static final int TRANSACTIONS = 300;
    private static final UriMatcher sUriMatcher = buildUriMatcher();
    private LendManagerDBHelper mLendManagerDBHelper;

    public static UriMatcher buildUriMatcher() {
        UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(LendManagerContract.AUTHORITY, LendManagerContract.PATH_MY_ACCOUNT, MY_ACCOUNT);
        uriMatcher.addURI(LendManagerContract.AUTHORITY, LendManagerContract.PATH_Friends, FRIENDS);
        uriMatcher.addURI(LendManagerContract.AUTHORITY, LendManagerContract.PATH_TRANSACTIONS, TRANSACTIONS);
        return uriMatcher;
    }

    @Override
    public boolean onCreate() {
        mLendManagerDBHelper = new LendManagerDBHelper(getContext());
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projections, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        final SQLiteDatabase db = mLendManagerDBHelper.getReadableDatabase();

        int match = sUriMatcher.match(uri);
        Cursor retCursor;
        switch (match) {
            case MY_ACCOUNT:
                retCursor = db.query(LendManagerContract.MyAccount.MY_ACCOUNT_TABLE_NAME,
                        projections,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder);
                break;
            case FRIENDS:
                retCursor = db.query(LendManagerContract.Friends.FRIENDS_TABLE_NAME,
                        projections,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder);
                break;
            case TRANSACTIONS:
                retCursor = db.query(LendManagerContract.Transactions.TRANSACTIONS_TABLE_NAME,
                        projections,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder);
                break;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
        retCursor.setNotificationUri(getContext().getContentResolver(), uri);

        return retCursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues contentValues) {
        final SQLiteDatabase db = mLendManagerDBHelper.getWritableDatabase();

        int match = sUriMatcher.match(uri);
        Uri retUri;
        long id;
        switch (match) {
            case MY_ACCOUNT:
                id = db.insert(LendManagerContract.MyAccount.MY_ACCOUNT_TABLE_NAME, null, contentValues);
                if (id > 0) {
                    retUri = ContentUris.withAppendedId(LendManagerContract.MyAccount.CONTENT_URI, id);
                } else {
                    throw new android.database.SQLException("Failed to insert row into " + uri);
                }
                break;
            case FRIENDS:
                id = db.insert(LendManagerContract.Friends.FRIENDS_TABLE_NAME, null, contentValues);
                if (id > 0) {
                    retUri = ContentUris.withAppendedId(LendManagerContract.Friends.CONTENT_URI, id);
                } else {
                    throw new android.database.SQLException("Failed to insert row into " + uri);
                }
                break;
            case TRANSACTIONS:
                //insertion of transaction in Transaction Table
                id = db.insert(LendManagerContract.Transactions.TRANSACTIONS_TABLE_NAME, null, contentValues);
                if (id > 0) {
                    retUri = ContentUris.withAppendedId(LendManagerContract.Transactions.CONTENT_URI, id);
                    //getting the id of friend with whom the transaction is made
                    int friendId = contentValues.getAsInteger(LendManagerContract.Transactions.FRIEND_ID);
                    Cursor cursor = query(LendManagerContract.Friends.CONTENT_URI,
                            null,
                            LendManagerContract.Friends.FRIEND_ID + " = " + friendId,
                            null,
                            null);

                    if (cursor.moveToNext()) {
                        ContentValues cv = new ContentValues();
                        //getting the current total of friend
                        double amount = cursor.getDouble(cursor.getColumnIndex(LendManagerContract.Friends.AMOUNT));
                        double cost = contentValues.getAsDouble(LendManagerContract.Transactions.AMOUNT);
                        amount += cost;
                        cv.put(LendManagerContract.Friends.AMOUNT, amount);
                        //updating the amount of friend in friends table
                        update(LendManagerContract.Friends.CONTENT_URI,
                                cv,
                                LendManagerContract.Friends.FRIEND_ID + " = " + friendId,
                                null);
                        updateUsersBalance(db, cost);
                    }
                } else {
                    throw new android.database.SQLException("Failed to insert row into " + uri);
                }
                break;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
        return retUri;
    }


    private void updateUsersBalance(SQLiteDatabase db, double cost) {
        Cursor cursor1 = db.query(LendManagerContract.MyAccount.MY_ACCOUNT_TABLE_NAME,
                null,
                null,
                null,
                null,
                null,
                null);
        double balance;
        if (cursor1.moveToNext()) {
            //getting the users current lend amount
            balance = cursor1.getDouble(cursor1.getColumnIndex(LendManagerContract.MyAccount.USER_BALANCE));
            ContentValues cv1 = new ContentValues();
            balance += cost;
            cv1.put(LendManagerContract.MyAccount.USER_BALANCE, balance);
            //updating the lend amount in MyAccount Table
            update(LendManagerContract.MyAccount.CONTENT_URI,
                    cv1,
                    null,
                    null);
        }
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] strings) {
        final SQLiteDatabase db = mLendManagerDBHelper.getWritableDatabase();
        int matchUri = sUriMatcher.match(uri);

        switch (matchUri) {
            case FRIENDS:
                Cursor cursor = db.query(LendManagerContract.Friends.FRIENDS_TABLE_NAME,
                        null,
                        selection,
                        null,
                        null,
                        null,
                        null);
                if (cursor.moveToNext()) {
                    double amount = cursor.getDouble(cursor.getColumnIndex(LendManagerContract.Friends.AMOUNT));
                    if (amount != 0) {
                        updateUsersBalance(db, amount*-1);
                    }
                }
                return db.delete(LendManagerContract.Friends.FRIENDS_TABLE_NAME,
                        selection,
                        strings);

            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues contentValues, @Nullable String
            selection, @Nullable String[] selectionArgs) {
        final SQLiteDatabase db = mLendManagerDBHelper.getWritableDatabase();
        int matchUri = sUriMatcher.match(uri);

        switch (matchUri) {
            case FRIENDS:
                return db.update(LendManagerContract.Friends.FRIENDS_TABLE_NAME,
                        contentValues,
                        selection,
                        selectionArgs);
            case MY_ACCOUNT:
                return db.update(LendManagerContract.MyAccount.MY_ACCOUNT_TABLE_NAME,
                        contentValues,
                        selection,
                        selectionArgs);
            default:
                throw new UnsupportedOperationException("Unknown Uri " + uri);
        }
    }
}
