/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ibm.drl.irexplanation.samplegen;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;

/**
 *
 * @author dganguly
 */
public abstract class BaseSampleGenerator {
    protected Properties prop;
    protected String idFieldName;
    protected String contentFieldName;
    protected Analyzer analyzer;
    
    public BaseSampleGenerator(Properties prop) {
        this.prop = prop;
        initFieldNames(prop);
        constructAnalyzer(prop);
    }
    
    public BaseSampleGenerator(String propFile) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(propFile));
        
        initFieldNames(prop);
        constructAnalyzer(prop);
    }
    
    public String[] analyze(Analyzer analyzer, String text) {
        
        List<String> buff = new ArrayList<>();
        try {
            TokenStream stream = analyzer.tokenStream("dummy", new StringReader(text));
            CharTermAttribute termAtt = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                String term = termAtt.toString();
                buff.add(term);
            }
            stream.end();
            stream.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
        
        String[] buffArray = new String[buff.size()];
        return buff.toArray(buffArray);
    }
    
    public String[] analyze(String text) {
        return analyze(analyzer, text);
    }
    
    final void initFieldNames(Properties prop) {
        idFieldName = prop.getProperty("field.id");
        contentFieldName = prop.getProperty("field.content");
    }
    
    final Analyzer constructAnalyzer(Properties prop) {
        if (analyzer == null)
            analyzer = new EnglishAnalyzer(
            StopFilter.makeStopSet(
                buildStopwordList(prop, "stopfile"))); // default analyzer
        return analyzer;
    }
    
    static List<String> buildStopwordList(Properties prop, String stopwordFileName) {
        List<String> stopwords = new ArrayList<>();
        String stopFile = prop.getProperty(stopwordFileName);        
        String line;

        try (FileReader fr = new FileReader(stopFile);
            BufferedReader br = new BufferedReader(fr)) {
            while ( (line = br.readLine()) != null ) {
                stopwords.add(line.trim());
            }
            br.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        return stopwords;
    }
    
    public TermWeights initTermWeights(ExplanationUnit expunit) throws Exception {
        String termText;
        BytesRef term;
        Terms tfvector;
        TermsEnum termsEnum;
        int tf;
        TermWeights twts = new TermWeights(prop);
        
        tfvector = expunit.reader.getTermVector(expunit.docId, contentFieldName);
        if (tfvector == null || tfvector.size() == 0)
            return null;
        
        // Construct the normalized tf vector
        termsEnum = tfvector.iterator(); // access the terms for this field
        
    	while ((term = termsEnum.next()) != null) { // explore the terms for this field
            termText = term.utf8ToString();
            tf = (int)termsEnum.totalTermFreq();
            
            String rawWord = expunit.deStem(termText);
            if (rawWord == null)
                rawWord = termText.length() > 2 ?
                        expunit.deStem(termText.substring(0, termText.length()-1)) : termText;
            if (rawWord == null)
                rawWord = termText;
            
            TermWeight wt = new TermWeight(rawWord, termText, tf);
            twts.add(wt);
        }
        
        return twts;
    }
    
    float getScore(InMemTermsIndexer indexer) {
        return 0;
    }
    
    public String getIDFieldName() {
        return idFieldName;
    }
    
    public String getContentFieldName() { return contentFieldName; }
}
