/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ibm.drl.irexplanation.samplegen;

import java.util.Properties;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;

/**
 *
 * @author dganguly
 */
public class PointwiseSampleGenerator extends BaseSampleGenerator {
    ExplanationUnit expunit;
    
    public PointwiseSampleGenerator(Properties prop,
            ExplanationUnit expunit) throws Exception {
        super(prop);
        init(expunit);
    }
    
    public PointwiseSampleGenerator(String propFile, ExplanationUnit expunit) throws Exception {
        super(propFile);
        init(expunit);
    }
    
    public Properties getProperties() { return prop; }
    
    final void init(ExplanationUnit expunit) throws Exception {
        this.expunit = expunit;        
        initTermWeights(expunit);
    }
    
    public IndexReader getReader() { return expunit.reader; }

    // Retrieval model score, i.e. sim(D, Q)
    @Override
    float getScore(InMemTermsIndexer inMemIndexer) {
        try {
            IndexReader inMemReader = inMemIndexer.getInMemReader();
            IndexSearcher inMemSearcher = new IndexSearcher(inMemReader);            
            inMemSearcher.setSimilarity(expunit.getSimilarity());
            
            TopDocs topDocs = inMemSearcher.search(expunit.getSampleQuery(), 1);
            return topDocs.scoreDocs.length==0? 0 : topDocs.scoreDocs[0].score;
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        
        return 0;
    }
    
    
}
