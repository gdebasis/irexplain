/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ibm.drl.irexplanation.regression;

import java.io.IOException;
import java.util.HashSet;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;

/**
 *
 * @author Procheta
 */
public class FeatureSpace {
    public double tf;
    public double docLength;
    public double df;
    
    public FeatureSpace(double tf, double df, double docLength){
        this.tf = tf;
        this.df = df;
        this.docLength = docLength;   
    }
    
    public FeatureSpace(){
        this.tf = 0;
        this.df = 0;
        this.docLength = 0;   
    }
    
    public FeatureSpace computeAvfFeatureValue(HashSet<String> queryWords,int i, IndexReader reader) throws IOException{
    String docText = reader.document(i).get("words");
    String ss[] = docText.split("\\s+");
    HashSet<String> nn = new HashSet<String>();
    for (String s : ss) {
            if (queryWords.contains(s)) {
                tf++;
                if (!nn.contains(s)) {
                    df += reader.docFreq(new Term("words", s));
                    nn.add(s);
                }
            }
        }
    tf/=nn.size();
    df/=nn.size();
    docLength= ss.length;
        return this;
    }
}
