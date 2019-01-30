/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ibm.drl.irexplanation.samplegen;

import ibm.drl.irexplanation.sampler.BaseSampler;
import ibm.drl.irexplanation.sampler.SamplerFactory;
import ibm.drl.irexplanation.trec.TRECQuery;
import ibm.drl.irexplanation.trec.TRECQueryBuilder;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.List;
import java.util.Properties;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;

/**
 *
 * @author dganguly
 */
public class PointwiseSampleGeneratorPipeline {
    Properties prop;
    int ntop;
    int nwanted;
    IndexReader reader;
    IndexSearcher searcher;
    InMemTermsIndexer inMemIndexer;
    BaseSampler sampler;
    int numSamples;
    FileWriter fw;
    BufferedWriter bw;
    String idFieldName;
    String contentFieldName;
    
    static final String DEFAULT_PROP = "init.properties";
    
    public PointwiseSampleGeneratorPipeline(String propFile) throws Exception {
        Properties defaultProp = new Properties();
        defaultProp.load(new FileReader(DEFAULT_PROP));
        
        prop = new Properties(defaultProp);
        prop.load(new FileReader(propFile));
        
        nwanted = Integer.parseInt(prop.getProperty("analysis.numtop", "1000"));
        ntop = Integer.parseInt(prop.getProperty("explanation.numtop", "5"));
        File indexDir = new File(prop.getProperty("index"));
        reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
        searcher = new IndexSearcher(reader);
        
        numSamples = Integer.parseInt(prop.getProperty("sampler.numsamples", "50"));
        
        fw = new FileWriter(prop.getProperty("sampling.outfile", "samples/pointwise/samples.txt"));
        bw = new BufferedWriter(fw);
        
        idFieldName = prop.getProperty("field.id");
        contentFieldName = prop.getProperty("field.content");        
    }

    public void generateSamplesForQuery(TRECQuery q) throws Exception {
        // Retrieving top 5 with scores
        TopDocs topDocs = searcher.search(q.getLuceneQueryObj(), nwanted);
        String simName = prop.getProperty("explanation.model", "lmjm");
        inMemIndexer = new InMemTermsIndexer(prop, searcher);
        inMemIndexer.indexAnalysisSet(topDocs);
        
        TermWeights twts;
        
        for (int i=0; i < ntop; i++) {
            
            ScoreDoc sd = topDocs.scoreDocs[i];
            ExplanationUnit eu = new ExplanationUnit(reader, sd.doc, simName, q, contentFieldName);
            
            String docName = ExplanationUnit.getDocName(reader, sd.doc, idFieldName);
            
            System.out.println(String.format(
                    "Generating samples for query-doc pair: (%s, %s)", q.id, docName));
            
            sampler = SamplerFactory.createSample(prop, eu);
            sampler.buildTermsInDoc();
            inMemIndexer.resetSampleId();
                    
            for (int j=0; j < numSamples; j++) {
                twts = sampler.nextSample();
                
                Document sampleDoc = inMemIndexer.addDocument(docName, twts);
                eu.setSample(idFieldName, sampleDoc);
                
                float score = sampler.getGen().getScore(inMemIndexer);
                
                bw.write(eu.toString() + "\t" + twts.toString() + "\t" + score);
                bw.newLine();
            }
            
        }
        inMemIndexer.close();
    }
    
    public void generateSamples() throws Exception {
        String queryFile = prop.getProperty("query.file");
        List<TRECQuery> queries =
                new TRECQueryBuilder(prop).constructQueries(queryFile);
        
        int start = Integer.parseInt(prop.getProperty("query.start", "0"));
        int end = Integer.parseInt(prop.getProperty("query.end", "-1"));
        
        for (TRECQuery query : queries) {
            if (!query.toExecute(start, end))
                continue;
            generateSamplesForQuery(query);
        }    
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("usage: java PointwiseSampleGeneratorPipeline <properties file>");
            args = new String[1];
            //args[0] = "pointwise.mask.properties";
            args[0] = "pointwise.tfidf.properties";
        }
        
        try {
            PointwiseSampleGeneratorPipeline gen = new PointwiseSampleGeneratorPipeline(args[0]);
            gen.generateSamples();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
