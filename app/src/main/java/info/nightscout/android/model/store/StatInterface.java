package info.nightscout.android.model.store;

import java.util.Date;

import io.realm.RealmModel;

public interface StatInterface  extends RealmModel {

    String toString();

    String getKey();

    void setKey(String key);

    Date getDate();

    void setDate(Date date);

}
