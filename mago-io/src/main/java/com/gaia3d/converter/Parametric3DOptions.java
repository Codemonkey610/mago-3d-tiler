package com.gaia3d.converter;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.proj4j.CoordinateReferenceSystem;

import java.util.List;

@Getter
@Setter
@Builder
public class Parametric3DOptions {
    private List<AttributeFilter> attributeFilters;

    private String heightColumnName;
    private String altitudeColumnName;
    private String diameterColumnName;
    private String startElevationColumnName;
    private String endElevationColumnName;
    private String elevationUnit;
    private String pipeElevationReference;
    private String burialDepthColumnName;
    private String pointElevationUnit;
    private String startGroundElevationColumnName;
    private String endGroundElevationColumnName;
    private String startBurialDepthColumnName;
    private String endBurialDepthColumnName;
    private String pipeBurialUnit;
    private String scaleColumnName;
    private String densityColumnName;
    private String headingColumnName;

    private double absoluteAltitudeValue;
    private double minimumHeightValue;
    private double skirtHeight;

    private double defaultDiameter;
    private double defaultHeight;
    private double defaultScale;
    private double defaultDensity;
    private double defaultHeading;
    private double burialDepthValue;
    private double pointVerticalOffset;
    private double pointModelHeight;
    /** Source-model local Y coordinate of its top surface. */
    private double pointModelTop;
    private boolean randomHeading;

    private CoordinateReferenceSystem sourceCrs;
    private CoordinateReferenceSystem targetCrs;

    private boolean flipCoordinate;
}
