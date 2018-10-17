/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ibm.drl.irexplanation.samplegen;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;

/**
 *
 * @author dganguly
 */
public class MaskedDoc {
    Document doc;

    public MaskedDoc(Document doc) {
        this.doc = doc;
    }
    
}
