package net.epsilonlabs.datamanagementefficient.library;

import java.util.ArrayList;
import java.util.Queue;

import net.epsilonlabs.datamanagementefficient.directive.CreateDirective;
import net.epsilonlabs.datamanagementefficient.directive.CreateReferenceDirective;
import net.epsilonlabs.datamanagementefficient.directive.DeleteDirective;
import net.epsilonlabs.datamanagementefficient.directive.DeleteReferenceDirective;
import net.epsilonlabs.datamanagementefficient.directive.Directive;
import net.epsilonlabs.datamanagementefficient.directive.UpdateDirective;
import net.epsilonlabs.datamanagementefficient.exception.DatabaseNotOpenExpection;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.SparseArray;

/**
 * The DataManagement library allows developers to easily store objects and to a local database without writing many lines of their own code.
 * This class gives access to many common database features. There are only two requirements for objects being stored in the database:
 * They must have an int field marked with the @Id annotation and all fields of that class must be of primitive type, String type, or
 * another storable type. The field marked with the @Id annotation is managed entirely by the database and should never be manually set, although
 * it may be accessed to help differentiate objects.
 * @author Tom Caputi
 *
 */
public class DataManager {

	private static DataManager instance;
	private SQLiteDatabase db = null;
	private PersistenceContext pc = null;
	private PersistenceManager pm = null;
	private boolean isOpen = false;
	private SQLHelper helper;

	/**
	 * Singleton instantiation method for getting a DataManager instance
	 * @param context the context that is instantiating the DataManager object
	 * @return a DataManager instance
	 */
	public static DataManager getInstance(Context context) {
		if (instance == null) instance = new DataManager(context);
		return instance;
	}

	/**
	 * Singleton private constructor for use by the getInstance() method.
	 * @param context the context that is instantiating the DataManager object
	 */
	private DataManager(Context context){
		this.helper = new SQLHelper(context);
	}

	/**
	 * Opens the database for writing.
	 */
	public void open(){
		db = helper.getWritableDatabase();
		pm = new PersistenceManager(db);
		pc = new PersistenceContext(pm);
		isOpen = true;
	}

	/**
	 * Closes the database. This method calls commit() finalizing any changes before closing.
	 */
	public void close(){
		if(!isOpen) throw new DatabaseNotOpenExpection();
		commit();
		db.close();
		isOpen = false;
	}

	/**
	 * Commits all cached changes to the database.
	 */
	public void commit(){
		if(!isOpen) throw new DatabaseNotOpenExpection();
		Queue<Directive> pendingDirectives = pc.getPendingDirectivesQueue();
		for(Directive directive : pendingDirectives){
			if(directive instanceof CreateDirective) pm.create((CreateDirective)directive);
			else if(directive instanceof DeleteDirective) pm.delete((DeleteDirective)directive);
			else if(directive instanceof UpdateDirective) pm.update((UpdateDirective)directive);
			else if(directive instanceof CreateReferenceDirective) pm.createReference((CreateReferenceDirective)directive);
			else if(directive instanceof DeleteReferenceDirective) pm.deleteReference((DeleteReferenceDirective)directive);
		}
		pc.clearPendingDirectivesQueue();
	}

	/**
	 * Adds an object to the database. This method will assign the object an id number and store it in the object's id field.
	 * @param obj the object to be stored.
	 * @return the assigned id number
	 */
	public int add(Object obj){
		if(!isOpen) throw new DatabaseNotOpenExpection();
		pc.create(obj);
		return DataUtil.getId(obj);
	}

	/**
	 * Deletes an object from the database. 
	 * @param cls the class of the object
	 * @param id the id number of the object
	 */
	public void delete(Class<?> cls, int id){
		if(!isOpen) throw new DatabaseNotOpenExpection();
		pc.delete(cls, id);
	}

	/**
	 * Updates an object in the database with a matching Class and id number.
	 * @param obj the object to be updated
	 */
	public <T> void update(T obj){
		if(!isOpen) throw new DatabaseNotOpenExpection();
		if(obj == null) throw new NullPointerException();
		pc.update(obj);
	}

	/**
	 * Retrieves an object from the database.
	 * @param cls the class of the object
	 * @param id the id number of the object
	 * @return
	 */
	public <T> T get(Class<T> cls, int id){
		if(!isOpen) throw new DatabaseNotOpenExpection();
		T object = pc.getFromCache(cls, id);
		if(object != null) return object;
		return pc.fetchToCache(cls, id);
	}

	/**
	 * Retrieves all stored objects of a given class n an ArrayList.
	 * @param cls the class
	 * @return an ArrayList of all stored objects of a given class
	 */
	public <T> ArrayList<T> getAll(Class<T> cls){
		if(!isOpen) throw new DatabaseNotOpenExpection();
		ArrayList<T> list = new ArrayList<T>();

		SparseArray<T> cachedObjects = pc.getAllFromCache(cls);
		for(int i=0; i<cachedObjects.size(); i++){
			list.add(cachedObjects.get(cachedObjects.keyAt(i)));
		}
		Cursor cursor = null;
		try{
			cursor = db.query(DataUtil.getTableName(cls), null, null, null, null, null, null);
		}catch(SQLException e){
			return list;
		}
		if(!cursor.moveToFirst()) return list;
		while(!cursor.isAfterLast()){
			int id = cursor.getInt(cursor.getColumnIndex(DataUtil.getIdField(cls).getName()));
			if(cachedObjects.get(id) == null){
				T object = pc.getFromCache(cls, id);
				if(object != null) list.add(object);
				list.add(pc.fetchToCache(cls, cursor));
			}
			cursor.moveToNext();
		}
		cursor.close();
		return list;
	}

	/**
	 * Returns the number of objects of a given class that are stored in the database.
	 * @param cls the class
	 * @return the number of stored objects of the given class
	 */
	public <T> int size(Class<T> cls){
		//TODO: fix this to work with the cache
		if(!isOpen) throw new DatabaseNotOpenExpection();
		return pm.size(cls);
	}

	/**
	 * Drops all records of a given class from the database.
	 * @param recordName the name of the class to be deleted
	 */
	public void dropRecords(String recordName){
		if(!isOpen) throw new DatabaseNotOpenExpection();
		pm.dropRecords(recordName);
	}

	/**
	 * Sets the default value to be given to added numerical fields when an class is changed. When fields are added to a stored class, DataManagement automatically
	 * adds these fields to existing objects in the database. If these fields are numerical, they can be given a default value when the objects are retrieved. If the
	 * added fields are not primitive, they are defaulted to null.
	 * @param value
	 */
	public void setDefaultUpgradeValue(int value){
		if(!isOpen) throw new DatabaseNotOpenExpection();
		pm.setDefaultUpgradeValue(value);
	}

	/**
	 * Returns true if the database is open.
	 * @return true if database is open
	 */
	public boolean isOpen(){
		return isOpen;
	}
}
