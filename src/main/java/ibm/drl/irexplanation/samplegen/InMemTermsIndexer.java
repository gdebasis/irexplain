/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ibm.drl.irexplanation.samplegen;

import java.util.Properties;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;

/**
 *
 * @author dganguly
 */
public class InMemTermsIndexer extends BaseSampleGenerator {
    int sampleId;
    IndexWriter writer;
    IndexSearcher searcher;
    IndexReader inMemReader;
    
    public InMemTermsIndexer(Properties prop, IndexSearcher searcher) throws Exception {
        super(prop);
        this.searcher = searcher;
        
        Directory ramdir = new RAMDirectory();                
        IndexWriterConfig iwcfg = new IndexWriterConfig(analyzer);
        writer = new IndexWriter(ramdir, iwcfg);        
    }
    
    void resetSampleId() { sampleId = 0; }

    IndexReader getInMemReader() throws Exception {
        inMemReader = DirectoryReader.open(writer.getDirectory());
        return inMemReader;
    }
    
    Document constructDoc(String docId, TermWeights twts) {
        Document doc = new Document();
        doc.add(new Field(idFieldName, docId + "_" + sampleId,
                Field.Store.YES, Field.Index.NOT_ANALYZED));
        
        StringBuffer buff = new StringBuffer();
        for (TermWeight tw: twts.getTermWeights()) {
            for (int i=0; i < tw.raw_tf; i++)
                buff.append(tw.term).append(" ");
        }
        
        doc.add(new Field(contentFieldName, buff.toString(),
                Field.Store.YES, Field.Index.ANALYZED));
        sampleId++;
        return doc;
    }
    
    // Create a local in-mem index for faster computations and analysis
    public void indexAnalysisSet(TopDocs topDocs) throws Exception {
        IndexReader reader = searcher.getIndexReader();
        
        for (ScoreDoc sd: topDocs.scoreDocs) {
            writer.addDocument(reader.document(sd.doc));
        }
        writer.commit();
    }
    
    public Document addDocument(String docId, TermWeights twts) throws Exception {
        Document doc = constructDoc(docId, twts);
        writer.addDocument(doc);
        writer.commit();
        return doc;
    }
    
    void close() throws Exception {
        writer.close();
    }
    
}
