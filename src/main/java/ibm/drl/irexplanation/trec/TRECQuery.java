/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ibm.drl.irexplanation.trec;

import org.apache.lucene.search.Query;

/**
 *
 * @author Debasis
 */
public class TRECQuery {
    public String       id;
    public String       title;
    public String       desc;
    public String       narr;
    public Query        luceneQuery;
    
    @Override
    public String toString() {
        return luceneQuery.toString();
    }

    public TRECQuery() {}
    
    public TRECQuery(TRECQuery that) { // copy constructor
        this.id = that.id;
        this.title = that.title;
        this.desc = that.desc;
        this.narr = that.narr;
    }

    public Query getLuceneQueryObj() { return luceneQuery; }

    public boolean toExecute(int start, int end) {
        if (end < start)
            return true;
        int qid = Integer.parseInt(id.trim());
        return start <= qid && qid < end;
    }
    
}
