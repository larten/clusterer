package io.nlopez.clusterer;

import com.google.android.gms.maps.model.LatLng;

import android.graphics.Point;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Nacho Lopez on 28/10/13.
 * Modified by Larten on 13/12/2013
 */
public class Cluster {

    private List<Clusterable> markers = new ArrayList<Clusterable>();
    private LatLng center;
    private Double latitudeSum;
    private Double longitudeSum;
	
	private Point position;

    public Cluster(Clusterable marker, Point position) {
        addMarker(marker);
		this.position = position;
    }

    public void addMarker(Clusterable marker) {
        markers.add(marker);
        if (center == null) {
            center = marker.getPosition();
            latitudeSum = center.latitude;
            longitudeSum = center.longitude;
        } else {
            latitudeSum += marker.getPosition().latitude;
            longitudeSum += marker.getPosition().longitude;
            center = new LatLng(latitudeSum/markers.size(), longitudeSum/markers.size());
        }
    }

    public List<Clusterable> getMarkers() {
        return markers;
    }

    public LatLng getCenter() {
        return center;
    }

    public boolean isCluster() {
        return getWeight() > 1;
    }

    public int getWeight() {
        return markers.size();
    }
	
    public Point getPosition() {
        return position;
    }
	
}
