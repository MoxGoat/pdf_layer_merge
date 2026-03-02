package com.geomerge.service;

import com.geomerge.exception.PdfMergeException;
import com.itextpdf.kernel.pdf.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Verifies that geospatial metadata survives the PDF page copy operation.
 *
 * Geospatial PDFs from ArcGIS/QGIS store spatial reference data in:
 * - /VP (Viewport) arrays on page dictionaries
 * - /Measure dictionaries within viewports (containing projection info)
 * - /GPTS arrays (geospatial coordinate points)
 * - /GCS dictionaries (geographic coordinate system / CRS)
 *
 * Since these are page-level dictionary entries, iText's copyPagesTo() should
 * preserve them via deep copy. This service acts as a safety net to verify
 * the data survived and throw clear errors if anything is missing.
 */
@Service
public class GeoMetadataService {

    private static final Logger log = LoggerFactory.getLogger(GeoMetadataService.class);

    // PdfName constants not pre-defined in iText for geospatial keys
    private static final PdfName VP = new PdfName("VP");
    private static final PdfName GPTS = new PdfName("GPTS");
    private static final PdfName LPTS = new PdfName("LPTS");
    private static final PdfName GCS = new PdfName("GCS");

    public void verifyViewports(PdfDocument src1, PdfDocument src2,
                                PdfDocument dest, int pagesFromSrc1) {
        int geoPageCount = 0;

        for (int i = 1; i <= dest.getNumberOfPages(); i++) {
            PdfPage destPage = dest.getPage(i);

            // Determine which source this page came from
            PdfDocument srcDoc = (i <= pagesFromSrc1) ? src1 : src2;
            int srcPageNum = (i <= pagesFromSrc1) ? i : (i - pagesFromSrc1);
            PdfPage srcPage = srcDoc.getPage(srcPageNum);

            boolean hasGeo = verifyPageViewport(srcPage, destPage, i);
            if (hasGeo) geoPageCount++;
        }

        if (geoPageCount > 0) {
            log.info("Geospatial metadata verified on {}/{} pages",
                    geoPageCount, dest.getNumberOfPages());
        } else {
            log.info("No geospatial viewport data found in source PDFs");
        }
    }

    /**
     * Verifies that a single page's geospatial viewport data survived the copy.
     * Returns true if the page has geospatial data.
     */
    private boolean verifyPageViewport(PdfPage srcPage, PdfPage destPage, int pageNum) {
        PdfArray srcVP = srcPage.getPdfObject().getAsArray(VP);

        if (srcVP == null) {
            // Source page has no geospatial data — nothing to verify
            return false;
        }

        PdfArray destVP = destPage.getPdfObject().getAsArray(VP);

        if (destVP == null) {
            throw new PdfMergeException(
                    "Geospatial viewport data lost during merge on page " + pageNum);
        }

        if (destVP.size() != srcVP.size()) {
            throw new PdfMergeException(
                    "Viewport count mismatch on page " + pageNum +
                            ": source has " + srcVP.size() + ", dest has " + destVP.size());
        }

        // Verify each viewport in the array
        for (int v = 0; v < srcVP.size(); v++) {
            PdfDictionary srcViewport = srcVP.getAsDictionary(v);
            PdfDictionary destViewport = destVP.getAsDictionary(v);

            if (srcViewport == null) continue;

            if (destViewport == null) {
                throw new PdfMergeException(
                        "Viewport " + v + " lost on page " + pageNum);
            }

            verifyMeasureDictionary(srcViewport, destViewport, pageNum, v);
        }

        log.debug("Page {}: {} viewport(s) verified", pageNum, srcVP.size());
        return true;
    }

    /**
     * Verifies the /Measure dictionary within a viewport, including
     * coordinate data (GPTS) and coordinate system (GCS).
     */
    private void verifyMeasureDictionary(PdfDictionary srcViewport, PdfDictionary destViewport,
                                         int pageNum, int viewportIndex) {
        PdfDictionary srcMeasure = srcViewport.getAsDictionary(PdfName.Measure);
        PdfDictionary destMeasure = destViewport.getAsDictionary(PdfName.Measure);

        if (srcMeasure == null) return; // No measure data in source

        if (destMeasure == null) {
            throw new PdfMergeException(
                    "Measure dictionary lost on page " + pageNum +
                            " viewport " + viewportIndex);
        }

        // Verify GPTS (geospatial coordinate points)
        PdfArray srcGPTS = srcMeasure.getAsArray(GPTS);
        PdfArray destGPTS = destMeasure.getAsArray(GPTS);
        if (srcGPTS != null && destGPTS == null) {
            throw new PdfMergeException(
                    "GPTS coordinate data lost on page " + pageNum +
                            " viewport " + viewportIndex);
        }

        // Verify LPTS (page coordinate points)
        PdfArray srcLPTS = srcMeasure.getAsArray(LPTS);
        PdfArray destLPTS = destMeasure.getAsArray(LPTS);
        if (srcLPTS != null && destLPTS == null) {
            throw new PdfMergeException(
                    "LPTS page coordinate data lost on page " + pageNum +
                            " viewport " + viewportIndex);
        }

        // Verify GCS (geographic coordinate system)
        PdfDictionary srcGCS = srcMeasure.getAsDictionary(GCS);
        PdfDictionary destGCS = destMeasure.getAsDictionary(GCS);
        if (srcGCS != null && destGCS == null) {
            throw new PdfMergeException(
                    "GCS coordinate system data lost on page " + pageNum +
                            " viewport " + viewportIndex);
        }

        // Verify coordinate point counts match
        if (srcGPTS != null && destGPTS != null && srcGPTS.size() != destGPTS.size()) {
            throw new PdfMergeException(
                    "GPTS coordinate count mismatch on page " + pageNum +
                            ": source has " + srcGPTS.size() + ", dest has " + destGPTS.size());
        }
    }
}
