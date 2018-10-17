/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ibm.drl.irexplanation.samplegen;

import java.util.HashMap;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;

/**
 *
 * @author dganguly
 */
public class SimFactory {
    static HashMap<String, Similarity> simMap;
    
    static void init() {
        simMap = new HashMap<>();
        simMap.put("lmjm", new LMJelinekMercerSimilarity(0.6f));
        simMap.put("lmdir", new LMDirichletSimilarity(1000));
        simMap.put("bm25", new BM25Similarity());
    }
    
    static Similarity createSim(String simName) {
        if (simMap == null)
            init();
        return simMap.get(simName);
    }
}
