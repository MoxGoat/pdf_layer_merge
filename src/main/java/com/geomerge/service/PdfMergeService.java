package com.geomerge.service;

import com.geomerge.exception.PdfMergeException;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@Service
public class PdfMergeService {

    private static final Logger log = LoggerFactory.getLogger(PdfMergeService.class);

    private final OcgMergeService ocgMergeService;
    private final GeoMetadataService geoMetadataService;

    public PdfMergeService(OcgMergeService ocgMergeService, GeoMetadataService geoMetadataService) {
        this.ocgMergeService = ocgMergeService;
        this.geoMetadataService = geoMetadataService;
    }

    public byte[] merge(InputStream pdf1Stream, InputStream pdf2Stream,
                        String file1Name, String file2Name) {
        if (pdf1Stream == null || pdf2Stream == null) {
            throw new PdfMergeException("Both PDF files must be provided");
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (
                PdfDocument src1 = new PdfDocument(new PdfReader(pdf1Stream));
                PdfDocument src2 = new PdfDocument(new PdfReader(pdf2Stream));
                PdfDocument dest = new PdfDocument(new PdfWriter(outputStream))
        ) {
            int src1Pages = src1.getNumberOfPages();
            int src2Pages = src2.getNumberOfPages();
            log.info("Source 1: {} pages, Source 2: {} pages", src1Pages, src2Pages);

            // Step 1: Copy all pages from both sources into destination
            src1.copyPagesTo(1, src1Pages, dest);
            src2.copyPagesTo(1, src2Pages, dest);
            log.info("Pages copied. Destination has {} pages", dest.getNumberOfPages());

            // Step 2: Merge OCG layer definitions into destination catalog
            ocgMergeService.mergeOcgProperties(src1, src2, dest, file1Name, file2Name);

            // Step 3: Verify geospatial metadata survived the copy
            geoMetadataService.verifyViewports(src1, src2, dest, src1Pages);

            log.info("Merge completed successfully");
        } catch (PdfMergeException e) {
            throw e;
        } catch (com.itextpdf.io.exceptions.IOException e) {
            throw new PdfMergeException("Invalid PDF file: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new PdfMergeException("Failed to merge PDFs: " + e.getMessage(), e);
        }

        return outputStream.toByteArray();
    }
}
