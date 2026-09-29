package com.gaia3d.converter.shape;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("default")
class ShapeConverterTest {

    @Test
    void parsesRectangularPipeMillimetersAsMeters() {
        assertArrayEquals(new float[]{3.8f, 5.8f},
                ShapeConverter.parseRectangularSizeInMeters("3800X5800"), 0.0001f);
        assertArrayEquals(new float[]{0.3f, 0.4f},
                ShapeConverter.parseRectangularSizeInMeters(" 300 × 400 "), 0.0001f);
    }

    @Test
    void keepsSingleDiameterInCircularPath() {
        assertNull(ShapeConverter.parseRectangularSizeInMeters("800"));
        assertNull(ShapeConverter.parseRectangularSizeInMeters(null));
    }

    @Test
    void rejectsNonPositiveRectangularDimensions() {
        assertThrows(IllegalArgumentException.class,
                () -> ShapeConverter.parseRectangularSizeInMeters("0X400"));
        assertThrows(IllegalArgumentException.class,
                () -> ShapeConverter.parseRectangularSizeInMeters("300X-1"));
    }
}
