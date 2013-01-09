package net.epsilonlabs.datamanagementefficient.test;

import net.epsilonlabs.datamanagementefficient.library.DataManager;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class TestActivity extends Activity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		TextView tv = new TextView(this);
		DataManager dm = DataManager.getInstance(this);
		dm.open();
		
		dm.get(DataSample.class, 1);
	
		dm.commit();
		dm.close();
		setContentView(tv);
	}
}
