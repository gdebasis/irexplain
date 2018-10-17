/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ibm.drl.irexplanation.trec;

import ibm.drl.irexplanation.samplegen.BaseSampleGenerator;
import java.util.*;

/**
 *
 * @author Debasis
 */
public class TRECQueryBuilder extends BaseSampleGenerator {
            
    public TRECQueryBuilder(Properties prop) {
        super(prop);
    }
    
    public List<TRECQuery> constructQueries(String queryFile) throws Exception {        
        TRECQueryParser parser = new TRECQueryParser(queryFile, analyzer, contentFieldName);
        parser.parse();
        return parser.getQueries();
    }

}
