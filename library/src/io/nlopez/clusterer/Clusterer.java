
package io.nlopez.clusterer;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import android.content.Context;
import android.graphics.Point;
import android.os.AsyncTask;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Nacho Lopez on 28/10/13.
 * Modified by Larten on 13/12/2013
 */
public class Clusterer {

    private static final int GRID_SIZE = 50;

    private GoogleMap googleMap;

    private Context context;

    private List<Clusterable> everyPOI = new ArrayList<Clusterable>();

    private HashMap<Point, Marker> visibleMarkers = new HashMap<Point, Marker>();

    private HashMap<Point, Cluster> previousClusters = new HashMap<Point, Cluster>();

    private float oldZoomValue = 0f;

    private LatLng oldLatLng;

    private OnPaintingClusterListener onPaintingCluster;

    private OnPaintingClusterableMarkerListener onPaintingMarker;

    private OnCameraChangeListener onCameraChangeListener;

    private UpdateMarkersTask task;

    private UpdateMarkersTask reDrawTask;

    private ExecutorService executor = Executors.newSingleThreadExecutor();

    public Clusterer(Context context, GoogleMap googleMap) {
        this.googleMap = googleMap;
        this.context = context;
        this.googleMap.setOnCameraChangeListener(cameraChanged);
    }

    GoogleMap.OnCameraChangeListener cameraChanged = new GoogleMap.OnCameraChangeListener() {

        @Override
        public void onCameraChange(CameraPosition cameraPosition) {
            if (oldZoomValue != cameraPosition.zoom) {
                oldZoomValue = cameraPosition.zoom;

                if (task != null) {
                    task.cancel(true);
                }

                if (reDrawTask != null) {
                    reDrawTask.cancel(true);
                }

                task = new UpdateMarkersTask(context, googleMap, onPaintingMarker,
                        onPaintingCluster, false);
                task.executeOnExecutor(executor, everyPOI);

            } else if (!cameraPosition.target.equals(oldLatLng)) {
                if (reDrawTask != null) {
                    reDrawTask.cancel(true);
                }

                oldLatLng = cameraPosition.target;
                reDrawTask = new UpdateMarkersTask(context, googleMap, onPaintingMarker,
                        onPaintingCluster, true);
                reDrawTask.executeOnExecutor(executor, everyPOI);
            }

            if (onCameraChangeListener != null) {
                onCameraChangeListener.onCameraChange(cameraPosition);
            }
        }
    };

    public void clear() {
        clearMarkers();
    }

    public void add(Clusterable marker) {
        everyPOI.add(marker);
    }

    public void addAll(List<Clusterable> markers) {
        this.everyPOI.addAll(markers);
    }

    public OnPaintingClusterListener getOnPaintingClusterListener() {
        return onPaintingCluster;
    }

    public void setOnPaintingClusterListener(OnPaintingClusterListener onPaintingCluster) {
        this.onPaintingCluster = onPaintingCluster;
    }

    public OnPaintingClusterableMarkerListener getOnPaintingMarkerListener() {
        return onPaintingMarker;
    }

    public void setOnPaintingMarkerListener(OnPaintingClusterableMarkerListener onPaintingMarker) {
        this.onPaintingMarker = onPaintingMarker;
    }

    public OnCameraChangeListener getOnCameraChangeListener() {
        return onCameraChangeListener;
    }

    public void setOnCameraChangeListener(OnCameraChangeListener onCameraChangeListener) {
        this.onCameraChangeListener = onCameraChangeListener;
    }

    protected void clearMarkers() {
        everyPOI = new ArrayList<Clusterable>();
    }

    private class UpdateMarkersTask extends
            AsyncTask<List<Clusterable>, Void, HashMap<Point, Cluster>> {

        private GoogleMap map;

        private OnPaintingClusterableMarkerListener onPaintingClusterableMarker;

        private OnPaintingClusterListener onPaintingCluster;

        private Projection projection;

        private int gridInPixels;

        private LatLngBounds bounds;

        private boolean onlyRedraw;

        UpdateMarkersTask(Context context, GoogleMap map,
                OnPaintingClusterableMarkerListener onPaintingClusterableMarker,
                OnPaintingClusterListener onPaintingCluster, boolean onlyRedraw) {
            this.gridInPixels = (int)(GRID_SIZE
                    * context.getResources().getDisplayMetrics().density + 0.5f);
            this.map = map;
            this.onPaintingCluster = onPaintingCluster;
            this.onPaintingClusterableMarker = onPaintingClusterableMarker;
            this.projection = map.getProjection();
            this.bounds = this.projection.getVisibleRegion().latLngBounds;
            this.onlyRedraw = onlyRedraw;
        }

        private boolean isInDistance(Point origin, Point other) {
            return origin.x >= other.x - gridInPixels && origin.x <= other.x + gridInPixels
                    && origin.y >= other.y - gridInPixels && origin.y <= other.y + gridInPixels;
        }

        @Override
        protected void onCancelled() {
            if (onlyRedraw) {
                Log.i("Clusterer", "onCancelled reDraw task");
            } else {
                Log.i("Clusterer", "onCancelled reCluster task");
            }
        }

        @Override
        protected HashMap<Point, Cluster> doInBackground(List<Clusterable>... params) {
            if (onlyRedraw) {
                Log.i("Clusterer", "doInBackground reDraw task");
            } else {
                Log.i("Clusterer", "doInBackground reCluster task");
            }
            HashMap<Point, Cluster> clusters = new HashMap<Point, Cluster>();

            if (onlyRedraw) {
                clusters.putAll(previousClusters);
            } else {
                for (Clusterable marker : params[0]) {
                    Point position = projection.toScreenLocation(marker.getPosition());
                    boolean addedToCluster = false;

                    for (Point storedPoint : clusters.keySet()) {

                        if (isInDistance(position, storedPoint)) {
                            clusters.get(storedPoint).addMarker(marker);
                            addedToCluster = true;
                            break;
                        }
                    }

                    if (!addedToCluster) {
                        clusters.put(position, new Cluster(marker, position));
                    }

                }
                previousClusters.clear();
                previousClusters.putAll(clusters);
            }

            return clusters;
        }

        @Override
        protected void onPostExecute(HashMap<Point, Cluster> result) {
            if (!onlyRedraw) {
                map.clear();
            }
            for (Cluster cluster : result.values()) {
                if (bounds.contains(cluster.getCenter())) {
                    if (!visibleMarkers.containsKey(cluster.getPosition())) {
                        if (cluster.isCluster()) {
                            if (onPaintingCluster != null) {
                                Marker marker = map.addMarker(onPaintingCluster
                                        .onCreateClusterMarkerOptions(cluster));
                                onPaintingCluster.onMarkerCreated(marker, cluster);
                                visibleMarkers.put(cluster.getPosition(), marker);
                            } else {
                                Marker marker = map.addMarker(new MarkerOptions()
                                        .position(cluster.getCenter())
                                        .title(Integer.valueOf(cluster.getWeight()).toString())
                                        .icon(BitmapDescriptorFactory
                                                .defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
                                visibleMarkers.put(cluster.getPosition(), marker);
                            }
                        } else {
                            if (onPaintingClusterableMarker != null) {
                                Marker marker = map.addMarker(onPaintingClusterableMarker
                                        .onCreateMarkerOptions(cluster.getMarkers().get(0)));
                                onPaintingClusterableMarker.onMarkerCreated(marker, cluster
                                        .getMarkers().get(0));
                                visibleMarkers.put(cluster.getPosition(), marker);
                            } else {
                                Marker marker = map.addMarker(new MarkerOptions().position(cluster
                                        .getCenter()));
                                visibleMarkers.put(cluster.getPosition(), marker);
                            }
                        }
                    }
                } else {
                    if (visibleMarkers.containsKey(cluster.getPosition())) {
                        visibleMarkers.get(cluster.getPosition()).remove();
                        visibleMarkers.remove(cluster.getPosition());
                    }
                }
            }
            if (onlyRedraw) {
                Log.i("Clusterer", "onPostExecute reDraw task");
            } else {
                Log.i("Clusterer", "onPostExecute reCluster task");
            }
        }

    }

    public interface OnPaintingClusterableMarkerListener {
        MarkerOptions onCreateMarkerOptions(Clusterable clusterable);

        void onMarkerCreated(Marker marker, Clusterable clusterable);
    }

    public interface OnPaintingClusterListener {
        MarkerOptions onCreateClusterMarkerOptions(Cluster cluster);

        void onMarkerCreated(Marker marker, Cluster cluster);
    }

    public interface OnCameraChangeListener {
        void onCameraChange(CameraPosition position);
    }

}
