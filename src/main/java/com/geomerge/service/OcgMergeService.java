package com.geomerge.service;

import com.itextpdf.kernel.pdf.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Reorganizes the OCG (layer) hierarchy in the merged PDF so that layers
 * are grouped under their source filename.
 *
 * iText's copyPagesTo() already handles the actual OCG merging via its
 * internal OcgPropertiesCopier. This service reads the merged result from
 * the destination catalog and rebuilds the /Order array to add
 * filename-based grouping.
 */
@Service
public class OcgMergeService {

    private static final Logger log = LoggerFactory.getLogger(OcgMergeService.class);

    public void mergeOcgProperties(PdfDocument src1, PdfDocument src2, PdfDocument dest,
                                    String file1Name, String file2Name) {
        String name1 = stripPdfExtension(file1Name != null ? file1Name : "PDF 1");
        String name2 = stripPdfExtension(file2Name != null ? file2Name : "PDF 2");

        // Collect OCG names from each source catalog to identify which source they came from
        Set<String> src1OcgNames = collectOcgNamesFromCatalog(src1);
        Set<String> src2OcgNames = collectOcgNamesFromCatalog(src2);

        log.info("Source 1 has {} layers, Source 2 has {} layers",
                src1OcgNames.size(), src2OcgNames.size());

        // Read the merged OCGs from the destination catalog (built by iText's OcgPropertiesCopier)
        PdfDictionary destOcProps = dest.getCatalog().getPdfObject()
                .getAsDictionary(PdfName.OCProperties);

        if (destOcProps == null) {
            log.info("No OCProperties in destination — skipping layer reorganization");
            return;
        }

        PdfArray destOcgs = destOcProps.getAsArray(PdfName.OCGs);
        if (destOcgs == null || destOcgs.isEmpty()) {
            log.info("No OCGs in destination catalog — skipping layer reorganization");
            return;
        }

        log.info("Destination has {} total OCG entries", destOcgs.size());

        // Partition dest OCGs into two groups based on which source they came from.
        // iText keeps original names for src1 and appends "_0", "_1" etc. for src2
        // when there are naming conflicts. So:
        //   - Layer with original name (no suffix) that exists in src1 → group1
        //   - Layer with a conflict suffix (name != baseName) → group2
        //   - Layer with original name only in src2 (no conflict) → group2
        List<PdfDictionary> group1 = new ArrayList<>();
        List<PdfDictionary> group2 = new ArrayList<>();
        List<PdfDictionary> ungrouped = new ArrayList<>();

        for (int i = 0; i < destOcgs.size(); i++) {
            PdfDictionary ocg = destOcgs.getAsDictionary(i);
            if (ocg == null) continue;

            String ocgName = getOcgName(ocg);
            if (ocgName == null) {
                ungrouped.add(ocg);
                continue;
            }

            String baseName = stripConflictSuffix(ocgName);
            boolean hasConflictSuffix = !baseName.equals(ocgName);

            if (hasConflictSuffix) {
                // iText renamed this layer — it came from the second source
                group2.add(ocg);
            } else if (src1OcgNames.contains(ocgName)) {
                // Original name, exists in src1 — belongs to first source
                group1.add(ocg);
            } else if (src2OcgNames.contains(ocgName)) {
                // Original name, only in src2 (no conflict) — belongs to second source
                group2.add(ocg);
            } else {
                ungrouped.add(ocg);
            }
        }

        log.info("Grouped: {} from '{}', {} from '{}', {} ungrouped",
                group1.size(), name1, group2.size(), name2, ungrouped.size());

        // Restore original layer names (remove "_0", "_1" suffixes iText added for conflicts)
        // Since layers are grouped under parent filenames, unique names aren't needed
        restoreOriginalNames(group1);
        restoreOriginalNames(group2);

        // Rebuild the /D (default configuration) with filename-based /Order grouping
        PdfDictionary defaultConfig = destOcProps.getAsDictionary(PdfName.D);
        if (defaultConfig == null) {
            defaultConfig = new PdfDictionary();
            destOcProps.put(PdfName.D, defaultConfig);
        }

        PdfArray order = new PdfArray();

        if (!group1.isEmpty()) {
            PdfArray g1 = new PdfArray();
            g1.add(new PdfString(name1));
            for (PdfDictionary ocg : group1) {
                g1.add(ocg);
            }
            order.add(g1);
        }

        if (!group2.isEmpty()) {
            PdfArray g2 = new PdfArray();
            g2.add(new PdfString(name2));
            for (PdfDictionary ocg : group2) {
                g2.add(ocg);
            }
            order.add(g2);
        }

        // Add any ungrouped layers at the top level
        for (PdfDictionary ocg : ungrouped) {
            order.add(ocg);
        }

        defaultConfig.put(PdfName.Order, order);
        defaultConfig.put(PdfName.Name, new PdfString("Merged Layers"));

        log.info("Layer order rebuilt with filename grouping");
    }

    /**
     * Reads OCG names from a document's catalog /OCProperties/OCGs array.
     */
    private Set<String> collectOcgNamesFromCatalog(PdfDocument doc) {
        Set<String> names = new LinkedHashSet<>();
        PdfDictionary ocProps = doc.getCatalog().getPdfObject()
                .getAsDictionary(PdfName.OCProperties);
        if (ocProps == null) return names;

        PdfArray ocgs = ocProps.getAsArray(PdfName.OCGs);
        if (ocgs == null) return names;

        for (int i = 0; i < ocgs.size(); i++) {
            PdfDictionary ocg = ocgs.getAsDictionary(i);
            if (ocg != null) {
                String name = getOcgName(ocg);
                if (name != null) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    private String getOcgName(PdfDictionary ocg) {
        PdfString name = ocg.getAsString(PdfName.Name);
        return name != null ? name.getValue() : null;
    }

    /**
     * iText's OcgPropertiesCopier appends "_0", "_1", etc. to resolve name conflicts.
     * Strip that suffix to match against original source names.
     */
    private String stripConflictSuffix(String name) {
        if (name == null) return null;
        // Match pattern like "LayerName_0", "LayerName_1"
        int lastUnderscore = name.lastIndexOf('_');
        if (lastUnderscore > 0 && lastUnderscore < name.length() - 1) {
            String suffix = name.substring(lastUnderscore + 1);
            if (suffix.matches("\\d+")) {
                return name.substring(0, lastUnderscore);
            }
        }
        return name;
    }

    /**
     * Removes the "_0", "_1" suffixes that iText's OcgPropertiesCopier added
     * to resolve name conflicts. Since layers are grouped under parent filenames,
     * they don't need unique names.
     */
    private void restoreOriginalNames(List<PdfDictionary> ocgs) {
        for (PdfDictionary ocg : ocgs) {
            String name = getOcgName(ocg);
            if (name != null) {
                String originalName = stripConflictSuffix(name);
                if (!originalName.equals(name)) {
                    ocg.put(PdfName.Name, new PdfString(originalName));
                }
            }
        }
    }

    private String stripPdfExtension(String filename) {
        if (filename.toLowerCase().endsWith(".pdf")) {
            return filename.substring(0, filename.length() - 4);
        }
        return filename;
    }
}
