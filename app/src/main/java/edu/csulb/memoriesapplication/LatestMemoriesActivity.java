package edu.csulb.memoriesapplication;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.transition.Slide;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SearchView;
import android.widget.Toast;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Created by Danie on 10/16/2017.
 */

public class LatestMemoriesActivity extends AppCompatActivity {
    //added
    private LinearLayoutManager linearLayoutManager;
    private RecyclerView recyclerView;
    private ArrayList<Polaroid> polaroids;
    private CardViewAdapter rvAdapter;
    private MyConstraintLayout constraintLayout;
    private ArrayList<String> urlList;
    private boolean accessLocationPermission;
    private static final int REQUEST_TAKE_PHOTO = 1;
    private static final int PERMISSION_REQUEST_CODE = 1052;
    private String TAG = "LatestMemoriesActivity";
    private SimpleExoPlayerView currentlyPlayingVideo;
    private String mCurrentPhotoPath;
    private boolean cameraRequest;
    private String city;
    private String state;
    private boolean queryFinished;
    private ProgressBar progressBar;
    private Query urlQuery;
    private SwipeRefreshLayout swipeRefreshLayout;
    private boolean userHasRefreshedAnimation;
    private SearchView searchView;

    //Value event listener which is first called with the initial query
    ValueEventListener valueEventListener = new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            for (DataSnapshot mediaSnapshot : dataSnapshot.getChildren()) {
                addToUrlList(mediaSnapshot);
            }
            //Data has finished loading
            queryFinished = true;
            progressBar.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            loadPolaroids();
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {
            Log.d(TAG, "valueEventListener: Database Error");
        }
    };


    ChildEventListener childEventListener = new ChildEventListener() {
        //A new item has been added to the database
        @Override
        public void onChildAdded(DataSnapshot dataSnapshot, String s) {
            //Only starts adding to the url list if the query is finished for the new ones
            if (queryFinished) {
                addToUrlList(dataSnapshot);
            }
        }

        @Override
        public void onChildChanged(DataSnapshot dataSnapshot, String s) {

        }

        public void onChildRemoved(DataSnapshot dataSnapshot) {

        }

        @Override
        public void onChildMoved(DataSnapshot dataSnapshot, String s) {

        }

        @Override
        public void onCancelled(DatabaseError databaseError) {
            Log.d(TAG, "childEventListener: Database Error");
        }
    };

    SwipeRefreshLayout.OnRefreshListener onRefreshListener = new SwipeRefreshLayout.OnRefreshListener() {
        @Override
        public void onRefresh() {
            userHasRefreshedAnimation = true;
            userHasRefreshed();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_latest);

        //Initialize the swipe refresh layout
        swipeRefreshLayout = (SwipeRefreshLayout) this.findViewById(R.id.latestSwipeLayout);
        swipeRefreshLayout.setOnRefreshListener(onRefreshListener);

        //Initialize the refresh boolean to that the user hasn't refreshed yet
        userHasRefreshedAnimation = false;

        //Initialize the progress bar to appear in the activity while the activity is in the process of querying
        progressBar = (ProgressBar) this.findViewById(R.id.latest_progress_bar);
        if (progressBar == null) {
            Log.d(TAG, "Progress Bar is null");
        }

        //set transitions
        setTransitions();

        //Variable to check if the user wants a camera request to change the behavior of onRequestPermissionsResult
        cameraRequest = false;

        //Check if we are able to access the user's location
        accessLocationPermission = UserPermission.checkUserPermission(this, UserPermission.Permission.LOCATION_PERMISSION);

        //If accessLocationPermission variable is false, cannot display anything... ask for user's permission
        Log.d(TAG, "Location permission = " + accessLocationPermission);
        if (!accessLocationPermission) {
            requestPermission();
        } else {
            //Application has permission to use the user's location, initialize the query
            Log.d(TAG, "Getting User location and initializing query");
            getUserLocationAndInitializeQuery(true);
        }

        //instantiate objects
        constraintLayout = (MyConstraintLayout) findViewById(R.id.constraintLayout);
        Intent startNeighborActivity = new Intent(this, TrendingActivity.class);
        startNeighborActivity.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        constraintLayout.setLeftPage(startNeighborActivity);
        polaroids = new ArrayList<Polaroid>();
        rvAdapter = new CardViewAdapter(this, polaroids);
        recyclerView = (RecyclerView) findViewById(R.id.recyclerView);
        linearLayoutManager = new LinearLayoutManager(this.getApplicationContext());
        recyclerView.setLayoutManager(linearLayoutManager);
        recyclerView.setAdapter(rvAdapter);
        //create custom onScrollChange listener to pause/play videos
        //based on scrolling
        recyclerView.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                int position1 = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstCompletelyVisibleItemPosition();
                int position2 = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
                Log.d("Latest Memories/first completely visible item position", "" + position1);
                if (position1 != -1) {
                    View view = (View) ((LinearLayoutManager) recyclerView.getLayoutManager()).findViewByPosition(position1);
                    if (view instanceof CardView) {
                        Log.d("View is of type", "CardView");
                        View childView = (View) ((CardView) view).getChildAt(0);
                        if (childView instanceof RelativeLayout) {
                            SimpleExoPlayerView videoView = (SimpleExoPlayerView) ((RelativeLayout) childView).getChildAt(0);
                            SimpleExoPlayer simpleExoPlayer = videoView.getPlayer();
                            simpleExoPlayer.setPlayWhenReady(true);
                            if (currentlyPlayingVideo != null && currentlyPlayingVideo != videoView) {
                                SimpleExoPlayer playerToPause = currentlyPlayingVideo.getPlayer();
                                playerToPause.setPlayWhenReady(false);
                            }
                            currentlyPlayingVideo = videoView;
                        } else if (currentlyPlayingVideo != null) {
                            SimpleExoPlayer playerToPause = currentlyPlayingVideo.getPlayer();
                            playerToPause.setPlayWhenReady(false);
                        }

                    }

                    //if the first visible view is not the first completely visible
                    //pause the video of the first completely visible if applicable
                    if (position2 != -1 && position2 != position1) {
                        Log.d("first visible item postion", "" + position2);
                        View view2 = (View) ((LinearLayoutManager) recyclerView.getLayoutManager()).findViewByPosition(position2);
                        View childView = (View) ((CardView) view2).getChildAt(0);
                        if (childView instanceof RelativeLayout) {
                            SimpleExoPlayerView videoView = (SimpleExoPlayerView) ((RelativeLayout) childView).getChildAt(0);
                            SimpleExoPlayer simpleExoPlayer = videoView.getPlayer();
                            simpleExoPlayer.setPlayWhenReady(false);
                        }
                    }
                }
            }
        });
    }

    @Override
    public void onRestart() {
        super.onRestart();
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        searchView.setQuery("", false);
        searchView.clearFocus();
        searchView.setIconified(true);
        if (urlQuery != null) {
            /*
            Attach a listener so that if any more media links are added to the database,
            they will be added to the top of the array list stack*/
            urlQuery.addChildEventListener(childEventListener);
            //Add listener so that we know the update finished
            urlQuery.addListenerForSingleValueEvent(valueEventListener);
        }
        if (accessLocationPermission) {
            getUserLocationAndInitializeQuery(true);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        //Remove the listeners
        urlQuery.removeEventListener(valueEventListener);
        urlQuery.removeEventListener(childEventListener);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.user_menu, menu);

        // Associate searchable configuration with the SearchView
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();

        // Override search hint
        searchView.setQueryHint(getResources().getString(R.string.search_hint));

        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (!query.isEmpty()) {
                    //Check if the query has a valid format with State, City
                    if (!query.contains(",")) {
                        Toast.makeText(LatestMemoriesActivity.this, "Invalid format", Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    String[] splitQuery = query.split(",");
                    String stateString = splitQuery[0].trim();
                    char[] stateCharArray = stateString.toCharArray();
                    //Immediately returns from the method if a bad character is found
                    for (char character : stateCharArray) {
                        int charValue = (int) character;
                        if (charValue < 65 || (charValue > 90 && charValue < 97) || charValue > 122) {
                            Toast.makeText(LatestMemoriesActivity.this, "Invalid character", Toast.LENGTH_SHORT).show();
                            return true;
                        }
                    }
                    String cityString = splitQuery[1].trim();
                    char[] cityCharArray = cityString.toCharArray();
                    for (char character : cityCharArray) {
                        int charValue = (int) character;
                        if (charValue < 65 || (charValue > 90 && charValue < 97) || charValue > 122) {
                            Toast.makeText(LatestMemoriesActivity.this, "Invalid character", Toast.LENGTH_SHORT).show();
                            return true;
                        }
                    }
                    Intent intent = new Intent(LatestMemoriesActivity.this, SearchResultsActivity.class);
                    intent.putExtra("state", stateString);
                    intent.putExtra("city", cityString);
                    startActivity(intent);
                }
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
        return true;
    }

    // Toolbar icon actions
    @Override
    public boolean onOptionsItemSelected(MenuItem items) {
        switch (items.getItemId()) {
            // Settings activity
            case R.id.action_settings: {
                final Intent intent = new Intent(LatestMemoriesActivity.this, Setting.class);
                startActivity(intent);
                Toast.makeText(getBaseContext(), "Information: ", Toast.LENGTH_SHORT).show();
                return true;
            }
            // Camera activity
            case R.id.action_cam: {
                if (accessLocationPermission) {
                    dispatchTakePictureIntent();
                    return true;
                } else {
                    cameraRequest = true;
                    requestPermission();
                }
            }
        }
        return super.onOptionsItemSelected(items);
    }

    //Asks for the user's permission, double check just in case, don't want to ask the user a second time
    private void requestPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSION_REQUEST_CODE);
        }
    }

    //Permission result received, act accordingly
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //Permission has been granted
                    accessLocationPermission = true;
                    if (cameraRequest) {
                        dispatchTakePictureIntent();
                        cameraRequest = false;
                    } else {
                        getUserLocationAndInitializeQuery(true);
                    }
                } else {
                    //Permission Denied
                    Toast.makeText(this, "Location Permission Denied", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    // Create Intent to start camera
    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "edu.csulb.memoriesapplication.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = new File(storageDir, "tempImage.jpg");
        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }

    // Called when camera activity ends
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_TAKE_PHOTO && resultCode == RESULT_OK) {
            // Create Intent with filepath of the photo taken
            Intent sendIntent = new Intent(LatestMemoriesActivity.this, DisplayActivity.class);

            sendIntent.putExtra("filepath", mCurrentPhotoPath);

            startActivity(sendIntent);
        }
    }

    /**
     * define the activity's transitions
     */
    public void setTransitions() {
        Slide enterSlide = new Slide();
        Slide exitSlide = new Slide();
        enterSlide.setDuration(500);
        enterSlide.setSlideEdge(Gravity.RIGHT);
        exitSlide.setDuration(500);
        exitSlide.setSlideEdge(Gravity.START);
        getWindow().setExitTransition(exitSlide);
        getWindow().setEnterTransition(enterSlide);
        getWindow().setReenterTransition(enterSlide);
        getWindow().setReturnTransition(enterSlide);
    }

    /**
     * parse through the urls contained in urlList
     * instatiate polaroids and add to polaroids ArrayList
     * notify the recyclerview adapter of change in dataset
     */
    private void loadPolaroids() {
        //new query, start from scratch
        //clear polaroids
        polaroids.clear();
        String combinedString;
        String uriString;
        char uriType;
        int size = urlList.size();
        Log.d("urlList size", "" + size);

        //parse each url in UrlLIst
        //urls are appended with "i" or "v' for image or video
        for (int i = 0; i < size; i++) {
            combinedString = urlList.get(i);
            Log.d("Combined String", combinedString);
            uriString = combinedString.substring(0, combinedString.length() - 2);
            Log.d("uriString", uriString);
            uriType = combinedString.charAt(combinedString.length() - 1);
            Log.d("uriType", "" + uriType);

            //if image url, send through imgUrl parameter
            if (uriType == 'i') {
                polaroids.add(new Polaroid(null, Uri.parse(uriString)));
                //else send through vidUrl parameter
            } else if (uriType == 'v') {
                Log.d("Latest loadPolaroid()", "loading video");
                polaroids.add(new Polaroid(Uri.parse(uriString), null));
            }

            //notifty adapter of dataset change
            rvAdapter.notifyDataSetChanged();
        }

        //If this was called when the user refreshed the application, end the animation here
        if (userHasRefreshedAnimation) {
            swipeRefreshLayout.setRefreshing(false);
            userHasRefreshedAnimation = false;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        //pause video if activity loses foreground
        if (currentlyPlayingVideo != null) {
            SimpleExoPlayer playerToPause = currentlyPlayingVideo.getPlayer();
            playerToPause.setPlayWhenReady(false);
        }
    }

    //Initialize Query parameter is there to see if the method should just update user location or initialize the query also
    private void getUserLocationAndInitializeQuery(final boolean initializeQueryBoolean) {
        FusedLocationProviderClient fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        try {
            fusedLocationProviderClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    if (location != null) {
                        double longitude = location.getLongitude();
                        double latitude = location.getLatitude();
                        Geocoder geocoder = new Geocoder(LatestMemoriesActivity.this, Locale.getDefault());
                        try {
                            List<Address> addressList = geocoder.getFromLocation(latitude, longitude, 1);
                            city = addressList.get(0).getLocality();
                            state = addressList.get(0).getAdminArea();
                            Log.d(TAG, "initiliazing query");
                            if (initializeQueryBoolean) {
                                initializeQuery();
                            }
                        } catch (IOException exception) {
                            exception.printStackTrace();
                        }
                    } else {
                        double longitude = -122.084;
                        double latitude = 37.422;
                        Geocoder geocoder = new Geocoder(LatestMemoriesActivity.this, Locale.getDefault());
                        try {
                            List<Address> addressList = geocoder.getFromLocation(latitude, longitude, 1);
                            city = "Long Beach";
                            state = "California";
                            Log.d(TAG, "initiliazing query");
                            if (initializeQueryBoolean) {
                                initializeQuery();
                            }
                        } catch (IOException exception) {
                            exception.printStackTrace();
                        }
                    }
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.d(TAG, "Failed to get location");
                }
            });
        } catch (SecurityException exception) {
            Log.d(TAG, "Security Exception : " + exception.toString());
        }
    }

    //Method that adds the url to the urlList ArrayList from a DataSnapshot object
    private void addToUrlList(DataSnapshot mediaSnapshot) {
        String urlString = (String) mediaSnapshot.child(GlobalDatabase.URL_KEY).getValue();
        String mediaType = (String) mediaSnapshot.child(GlobalDatabase.MEDIA_TYPE_KEY).getValue();
        if (mediaType.charAt(0) == 'i') {
            urlString = urlString + 'i';
        } else if (mediaType.charAt(0) == 'v') {
            urlString = urlString + 'v';
        }
        urlList.add(0, urlString);
    }

    //Retrieves a list of url links and returns null for an empty list
    private void initializeQuery() {
        //Initialize the progress bar to appear in the activity while the activity is in the process of querying
        progressBar.setVisibility(View.VISIBLE);
        //Query just started, initialize the query to change behavior of child listener
        queryFinished = false;
        //Creates a reference for the location where every media link is stored ordered by time
        DatabaseReference databaseReference = GlobalDatabase.getMediaListReference(state);
        //Maximum amount of querries
        final int maxQuerryCount = 700;
        //Initialize the query
        urlQuery = databaseReference.orderByChild(GlobalDatabase.CITY_KEY).equalTo(city).limitToLast(maxQuerryCount);
        Log.d(TAG, "Current City: " + city);
        //Add listener so that we know the update finished
        urlQuery.addListenerForSingleValueEvent(valueEventListener);
        /*
        Attach a listener so that if any more media links are added to the database,
        they will be added to the top of the array list stack*/
        urlQuery.addChildEventListener(childEventListener);
        //Initialize the ArrayList to hold the url strings
        urlList = new ArrayList<>();
    }

    //refresh the recycleview with a new query
    //if the user has chosen to refresh
    private void userHasRefreshed() {
        getUserLocationAndInitializeQuery(true);
        recyclerView.scrollToPosition(0);
        loadPolaroids();
    }

}
