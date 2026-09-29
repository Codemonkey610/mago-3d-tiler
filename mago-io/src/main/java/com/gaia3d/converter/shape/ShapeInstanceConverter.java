package com.gaia3d.converter.shape;

import com.gaia3d.converter.AttributeFilter;
import com.gaia3d.converter.Parametric3DOptions;
import com.gaia3d.converter.kml.AttributeReader;
import com.gaia3d.converter.kml.TileTransformInfo;
import com.gaia3d.util.GlobeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.geotools.api.data.Query;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.filter.Filter;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.files.ShpFiles;
import org.geotools.data.shapefile.shp.ShapefileReader;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.util.factory.Hints;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * KmlReader is a class that reads kml files.
 * It reads kml files and returns the information of the kml file.
 */
@Slf4j
@RequiredArgsConstructor
public class ShapeInstanceConverter implements AttributeReader {

    private final Parametric3DOptions parametricOptions;

    // read kml file
    @Override
    public TileTransformInfo read(File file) {
        log.error("[ERROR] ShapePointReader read method is not implemented yet.");
        return null;
    }

    @Override
    public List<TileTransformInfo> readAll(File file) {
        List<TileTransformInfo> result = new ArrayList<>();
        readEach(file, result::add);
        return result;
    }

    @Override
    public void readEach(File file, Consumer<TileTransformInfo> consumer) {
        List<AttributeFilter> attributeFilters = parametricOptions.getAttributeFilters();
        boolean isDefaultCrs = Objects.equals(parametricOptions.getSourceCrs(),
                new CRSFactory().createFromName("EPSG:3857"));
        String altitudeColumnName = parametricOptions.getAltitudeColumnName();
        String headingColumnName = parametricOptions.getHeadingColumnName();
        String scaleColumnName = parametricOptions.getScaleColumnName();
        String densityColumnName = parametricOptions.getDensityColumnName();
        String burialDepthColumnName = parametricOptions.getBurialDepthColumnName();

        ShpFiles shpFiles = null;
        ShapefileReader reader = null;
        ShapefileDataStore dataStore = null;
        try {
            shpFiles = new ShpFiles(file);
            reader = new ShapefileReader(shpFiles, true, true, new GeometryFactory());
            dataStore = new ShapefileDataStore(file.toURI().toURL());

            ShapeEncodingFix shapeEncodingFix = new ShapeEncodingFix();
            dataStore.setCharset(shapeEncodingFix.detectCharset(file));

            String typeName = dataStore.getTypeNames()[0];
            SimpleFeatureSource source = dataStore.getFeatureSource(typeName);
            var query = new Query(typeName, Filter.INCLUDE);
            query.getHints().add(new Hints(Hints.FEATURE_2D, true));

            SimpleFeatureCollection features = source.getFeatures(query);
            var coordinateReferenceSystem = features.getSchema().getCoordinateReferenceSystem();
            if (isDefaultCrs && coordinateReferenceSystem != null) {
                CoordinateReferenceSystem crs = GlobeUtils.convertProj4jCrsFromGeotoolsCrs(coordinateReferenceSystem);
                log.info(" - Coordinate Reference System : {}", crs.getName());
                parametricOptions.setSourceCrs(crs);
            }

            int featureIndex = 0;
            int featuresCount = source.getCount(query);
            try (FeatureIterator<SimpleFeature> iterator = features.features()) {
                while (iterator.hasNext()) {
                    featureIndex++;
                    log.info(" - Processing feature {}/{}", featureIndex, featuresCount);

                    SimpleFeature feature = iterator.next();
                    Geometry geom = (Geometry) feature.getDefaultGeometry();

                    double defaultHeading = parametricOptions.getDefaultHeading();
                    if (parametricOptions.isRandomHeading()) {
                        defaultHeading = Math.random() * 360.0;
                    }
                    double heading = getNumberAttribute(feature, headingColumnName, defaultHeading);
                    double altitude = getNumberAttribute(feature, altitudeColumnName,
                            parametricOptions.getAbsoluteAltitudeValue());
                    double burialDepth = burialDepthColumnName == null
                            ? parametricOptions.getBurialDepthValue()
                            : getNumberAttribute(feature, burialDepthColumnName, 0.0d);
                    double unitFactor = elevationUnitFactor(parametricOptions.getPointElevationUnit());
                    altitude = altitude * unitFactor + parametricOptions.getPointVerticalOffset();
                    double scale = getNumberAttribute(feature, scaleColumnName, parametricOptions.getDefaultScale());
                    double density = getNumberAttribute(feature, densityColumnName,
                            parametricOptions.getDefaultDensity());

                    if (!attributeFilters.isEmpty()) {
                        boolean filterFlag = false;
                        for (AttributeFilter attributeFilter : attributeFilters) {
                            String columnName = attributeFilter.getAttributeName();
                            String filterValue = attributeFilter.getAttributeValue();
                            String attributeValue = castStringFromObject(feature.getAttribute(columnName), "null");
                            if (filterValue.equals(attributeValue)) {
                                filterFlag = true;
                                break;
                            }
                        }
                        if (!filterFlag) {
                            continue;
                        }
                    }

                    if (geom == null) {
                        continue;
                    }

                    Map<String, String> attributes = extractAttributes(feature);
                    double depthMeters = burialDepth * unitFactor;
                    if (depthMeters > 0.0d && parametricOptions.getPointModelHeight() > 0.001d
                            && geom instanceof org.locationtech.jts.geom.Point point) {
                        // Well depth is an absolute vertical target. Horizontal model calibration
                        // must not shorten or lengthen the requested depth.
                        double verticalScale = depthMeters / parametricOptions.getPointModelHeight();
                        // Align the GLB's actual top surface with the requested cover elevation.
                        // Models are not guaranteed to be centered around their local origin.
                        // glTF instance models are Y-up. The instance rotation places that local Y
                        // axis upright in the tileset; POSITION component reordering does not change
                        // the model's local scale axes. Stretch Y only so burial depth increases the
                        // well height without widening its footprint.
                        double topOffset = parametricOptions.getPointModelTop() * verticalScale;
                        emitPointWithScale("I3dmFromShape", point, altitude - topOffset,
                                heading, scale, verticalScale, scale, attributes,
                                parametricOptions.getSourceCrs(), consumer);
                    } else {
                        emitTileTransformInfos("I3dmFromShape", geom, coordinateReferenceSystem, density, scale,
                                altitude, heading, attributes, parametricOptions.getSourceCrs(), consumer);
                    }
                }
            }
        } catch (IOException e) {
            log.error("[ERROR] :", e);
            throw new RuntimeException(e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    log.debug("Failed to close shapefile reader.", e);
                }
            }
            if (shpFiles != null) {
                shpFiles.dispose();
            }
            if (dataStore != null) {
                dataStore.dispose();
            }
        }
    }

    private double getNumberAttribute(SimpleFeature feature, String column, double defaultValue) {
        if (column == null || column.isBlank()) {
            return defaultValue;
        }
        double result = defaultValue;
        Object attributeLower = feature.getAttribute(column);
        Object attributeUpper = feature.getAttribute(column.toUpperCase());
        Object attributeObject = null;
        if (attributeLower != null) {
            attributeObject = attributeLower;
        } else if (attributeUpper != null) {
            attributeObject = attributeUpper;
        }

        if (attributeObject instanceof Short) {
            result = (short) attributeObject;
        } else if (attributeObject instanceof Integer) {
            result = (int) attributeObject;
        } else if (attributeObject instanceof Long) {
            result = (Long) attributeObject;
        } else if (attributeObject instanceof Double) {
            result = (double) attributeObject;
        } else if (attributeObject instanceof String) {
            result = Double.parseDouble((String) attributeObject);
        }
        return result;
    }

    private double elevationUnitFactor(String unit) {
        if (unit == null) {
            return 1.0d;
        }
        return switch (unit.toLowerCase()) {
            case "mm" -> 0.001d;
            case "cm" -> 0.01d;
            default -> 1.0d;
        };
    }

}
