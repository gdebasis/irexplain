/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ibm.drl.irexplanation.samplegen;

import ibm.drl.irexplanation.trec.TRECQuery;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.similarities.Similarity;

/**
 *
 * @author dganguly
 */
public class ExplanationUnit {
    IndexReader reader;
    int docId;
    Similarity sim;
    TRECQuery query;
    
    Document sampleDoc;
    String sampleDocId;
    Query sampleQuery;
    
    public ExplanationUnit(IndexReader reader,
            int docId, String simName,
            TRECQuery query) {
        this.reader = reader;
        this.docId = docId;
        this.sim = SimFactory.createSim(simName);
        this.query = query;
    }
    
    public static String getDocName(IndexReader reader, int docId, String idFieldName) {
        try {
            return reader.document(docId).get(idFieldName);
        }
        catch (Exception ex) {
            return null;
        }
    }
    
    public static String getDocName(Document doc, String idFieldName) {
        try {
            return doc.get(idFieldName);
        }
        catch (Exception ex) {
            return null;
        }
    }
    
    public void setSample(String idFieldName, Document sampleDoc) {
        this.sampleDoc = sampleDoc;
        
        sampleDocId = getDocName(sampleDoc, idFieldName);
        // The query for matching the exact sample contains the sample doc-id
        // as a part of the query.        
        BooleanQuery bq = new BooleanQuery();
        bq.add(this.getQuery(), BooleanClause.Occur.SHOULD);
        bq.add(new BooleanClause(new TermQuery(new Term(idFieldName, sampleDocId)), BooleanClause.Occur.MUST));
        
        this.sampleQuery = bq;
    }
    
    public IndexReader getReader() { return reader; }
    public Similarity getSimilarity() { return sim; }
    public Query getQuery() { return query.getLuceneQueryObj(); }
    public Query getSampleQuery() { return this.sampleQuery; }
    
    @Override
    public String toString() {
        StringBuffer buff = new StringBuffer();
        buff
            .append(query.id)
            .append("\t")
            .append(sampleDocId)
            .append("\t");
        
        return buff.toString();
    }
}
