/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ibm.drl.irexplanation.regression;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;

/**
 *
 * @author Procheta
 */
public class RankingModelRegression {

    IndexSearcher searcher;
    IndexReader reader;
    ArrayList<Similarity> simClasses;
    String stopFileName;
    ArrayList<String> queries;
    String queryFilePath;
    double avgLength;
    String rankingModel;
    int topk;

    public RankingModelRegression(String propFilename) throws FileNotFoundException, IOException {
        Properties prop = new Properties();
        prop.load(new FileReader(propFilename));
        File indexDir = new File(prop.getProperty("index"));
        topk = Integer.parseInt(prop.getProperty("topk"));
        reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
        stopFileName = prop.getProperty("stop");
        searcher = new IndexSearcher(reader);
        simClasses = new ArrayList<>();
        Similarity sim = new BM25Similarity();
        simClasses.add(sim);
        sim = new LMDirichletSimilarity();
        simClasses.add(sim);
        sim = new LMJelinekMercerSimilarity(0.6f);
        simClasses.add(sim);
        getQueries();
        avgLength = computeAvglength();
    }

    public void getQueries() throws FileNotFoundException, IOException {
        FileReader fr = new FileReader(new File(queryFilePath));
        BufferedReader br = new BufferedReader(fr);
        queries = new ArrayList<>();
        String line = br.readLine();

        while (line != null) {
            line = line.replaceAll("<title>", "");
            line = line.replaceAll("</title>", "");
            queries.add(line);
            line = br.readLine();
        }
    }

    Query buildQuery(String queryStr) throws Exception {
        BooleanQuery q = new BooleanQuery();
        Term thisTerm = null;
        Query tq = null;
        String[] queryWords = analyze(queryStr).split("\\s+");

        // search in title and content...
        for (String term : queryWords) {
            thisTerm = new Term("words", term);
            tq = new TermQuery(thisTerm);
            q.add(tq, BooleanClause.Occur.SHOULD);
        }
        return q;
    }

    String analyze(String query) throws Exception {
        StringBuffer buff = new StringBuffer();
        TokenStream stream = new EnglishAnalyzer(StopFilter.makeStopSet(buildStopwordList(stopFileName))).tokenStream("dummy", new StringReader(query));
        CharTermAttribute termAtt = stream.addAttribute(CharTermAttribute.class);
        stream.reset();
        while (stream.incrementToken()) {
            String term = termAtt.toString();
            term = term.toLowerCase();
            buff.append(term).append(" ");
        }
        stream.end();
        stream.close();
        return buff.toString();
    }


    public double computeBM25score(String word, double freq, double length, double docfreq, double avgLength) throws IOException {
        double k1 = 1;
        double b = 1;
        double idf = 1;
        if (docfreq != 0) {
            idf = Math.log(reader.numDocs() / docfreq);
        }
        double cf = reader.totalTermFreq(new Term("words", word));
        CollectionStatistics collectionStats = searcher.collectionStatistics("words");
        double token_count = collectionStats.sumTotalTermFreq();
        double score = ((freq * (k1 + 1)) / (freq + k1 * (1 - b + b * (length / avgLength)))) * idf;
        return score;
    }

    public double computeRankingModelScore(String word, double freq, double length, double docfreq, double avgLength) throws IOException {

        return computeBM25score(word, freq, length, docfreq, avgLength);
    }

    public void WritePerQueryDocScore(String writeFilePath) throws FileNotFoundException, IOException, Exception {
        FileWriter fw = null;
        BufferedWriter bw = null;

        for (int i = 0; i < queries.size(); i++) {
            fw = new FileWriter(new File(writeFilePath + String.valueOf(i) + "_100.txt"));
            bw = new BufferedWriter(fw);
            bw.write("Query\tDocument\tlabel\tDocId\tScore");
            bw.newLine();
            String qryString = queries.get(i);
            Query q = buildQuery(qryString);
            TopDocs tdocs = searcher.search(q, topk);
            if (tdocs.scoreDocs.length > 0) {
                int length = tdocs.scoreDocs.length;
                for (int i1 = 0; i1 < length; i1++) {
                    int docId = tdocs.scoreDocs[i1].doc;
                    String words = analyze(reader.document(docId).get("words"));
                    bw.write(qryString + "\t" + words + "\t1" + "\t" + reader.document(docId).get("id") + "\t" + String.valueOf(tdocs.scoreDocs[i].score));
                    bw.newLine();
                }
            }
            bw.close();
        }
    }

    public HashMap<String, Integer> getWordCOuntMap(String words) {
        String w[] = words.split("\\s+");

        HashMap<String, Integer> wordMap = new HashMap();
        for (String s : w) {
            s = s.replaceAll("words:", "");
            if (wordMap.containsKey(s)) {
                wordMap.put(s, wordMap.get(s) + 1);
            } else {
                wordMap.put(s, 1);
            }
        }
        return wordMap;
    }

    public List<String> buildStopwordList(String stopwordFileName) throws FileNotFoundException, IOException {
        List<String> stopwords = new ArrayList();
        String stopFile = stopwordFileName;
        String line;

        FileReader fr = new FileReader(stopFile);
        BufferedReader br = new BufferedReader(fr);
        while ((line = br.readLine()) != null) {
            stopwords.add(line.trim());
        }
        br.close();
        return stopwords;
    }

    public void writeScoreForSingleQuery(String writeFile, String query) throws FileNotFoundException, IOException, Exception {
        Query q = buildQuery(query);
        HashSet<String> queryWords = (HashSet<String>) getWordCOuntMap(analyze(q.toString())).keySet();
        TopDocs tdocs = searcher.search(q, topk);
        FileWriter fw = new FileWriter(new File(writeFile));
        BufferedWriter bw = new BufferedWriter(fw);
        int length = tdocs.scoreDocs.length;
        for (int i = 0; i < length; i++) {
            Document topdoc = reader.document(tdocs.scoreDocs[i].doc);
            String words = topdoc.get("words");
            words = analyze(words);
            HashMap<String, Integer> wordMap = getWordCOuntMap(words);
            Iterator it = wordMap.keySet().iterator();
            double docLength = words.split("\\s+").length;
            double score = 0;
            while (it.hasNext()) {
                String st = (String) it.next();
                double docFreq = reader.docFreq(new Term("words", st));
                score = computeRankingModelScore(st, wordMap.get(st), length, docFreq, avgLength);
                double tf = wordMap.get(st);
                String isQueryWord = "0";
                if (queryWords.contains(st)) {
                    isQueryWord = "1";
                }
                bw.write(st + "\t" + String.valueOf(Math.sqrt(tf)) + "\t" + String.valueOf(tf) + "\t" + String.valueOf(tf * tf) + "\t" + String.valueOf(Math.sqrt(length)) + "\t" + String.valueOf(length) + "\t" + String.valueOf(length * length) + "\t" + String.valueOf(Math.sqrt(docFreq)) + "\t" + String.valueOf(docFreq) + "\t" + String.valueOf(docFreq * docFreq) + "\t" + isQueryWord + "\t" + String.valueOf(score));
                bw.newLine();
            }
        }
    }

    public double computeAvglength() throws IOException {
        double avglength = 0;
        for (int i = 0; i < reader.numDocs(); i++) {
            String words = reader.document(i).get("words");
            avglength += words.split("\\s+").length;
        }
        avglength /= reader.numDocs();
        return avglength;
    }

    public void computeMaxStat() throws IOException, Exception {
        double maxtf = 0;
        double maxDf = 0;
        HashSet<String> wordSet = new HashSet<String>();
        for (int i = 0; i < reader.numDocs(); i++) {
            String id = reader.document(i).get("id");
            String words = reader.document(i).get("words");
            words = analyze(words);
            String stt[] = words.split("\\s+");
            HashMap<String, Integer> wordMap = getWordCOuntMap(words);
            Iterator it = wordMap.keySet().iterator();
            double length = stt.length;
            while (it.hasNext()) {
                String st = (String) it.next();
                double tf = wordMap.get(st);
                if (tf > maxtf) {
                    maxtf = tf;
                }
                /* if (docFreq > maxDf) {
                        maxDf = docFreq;
                    }*/
                //}
                wordSet.add(st);
            }
        }
        System.out.println(maxDf);
        System.out.println(maxtf);
    }

    public void writeScoreForSingLeQueryDeepLearning(String DocIdMappingFile, String DRMMScoreFile, String writeFile, String qid) throws FileNotFoundException, IOException, Exception {
        FileReader fr = new FileReader(new File(DocIdMappingFile));
        BufferedReader br = new BufferedReader(fr);

        String line = br.readLine();
        HashMap<String, String> wordMap = new HashMap<String, String>();
        while (line != null) {
            String st[] = line.split("\t");
            wordMap.put(st[0], st[1]);
            line = br.readLine();
        }
        fr = new FileReader(new File(DRMMScoreFile));
        br = new BufferedReader(fr);

        FileWriter fw = new FileWriter(new File(writeFile));
        BufferedWriter bw = new BufferedWriter(fw);
        line = br.readLine();
        String prev = line.split("\t")[0];

        while (line != null) {
            String st[] = line.split("\t");
            if (!prev.equals(st[0])) {
                bw.close();
                break;
            }
            String pid = st[1];
            String passage = wordMap.get(pid);
            String word = st[2];
            passage = analyze(passage);
            String ss[] = passage.split("\\s+");
            double docLength = ss.length;
            double tf = 0;
            double docfreq = reader.docFreq(new Term("words", word));
            for (String s : ss) {
                if (s.equals(word)) {
                    tf++;
                }
            }
            if (!word.equals("<OOV>") && !word.equals("<PAD>")) {
                bw.write(word + "\t" + String.valueOf(Math.sqrt(tf)) + "\t" + String.valueOf(tf) + "\t" + String.valueOf(tf * tf) + "\t" + String.valueOf(Math.sqrt(docLength)) + "\t" + String.valueOf(docLength) + "\t" + String.valueOf(docLength * docLength) + "\t" + String.valueOf(Math.sqrt(docfreq)) + "\t" + String.valueOf(docfreq) + "\t" + String.valueOf(docfreq * docfreq) + "\t" + st[3] + "\t" + pid);
                bw.newLine();
            }
            prev = line.split("\t")[0];
            line = br.readLine();
        }
    }

    public void findRankDiffAcrossModels(Query q, int i, int j, String scoreFile) throws IOException, Exception {
        FileReader fr = new FileReader(new File(scoreFile));
        BufferedReader br = new BufferedReader(fr);

        String line = br.readLine();
       
        searcher.setSimilarity(simClasses.get(i));
        ArrayList<String> firstModelDocIds = new ArrayList<String>();
        TopDocs tdocs = searcher.search(q, topk);

        int length = tdocs.scoreDocs.length;
        for (int i1 = 0; i1 < length; i1++) {
            int id = tdocs.scoreDocs[i].doc;
            firstModelDocIds.add(reader.document(id).get("id"));
        }
        searcher.setSimilarity(simClasses.get(j));
        TopDocs tdocs1 = searcher.search(q, topk);
        length = tdocs1.scoreDocs.length;
        int index = 0;
        
        double maxDiff =-1;
        int id = 0;
        for (int i1 = 0; i1 < length; i1++) {
            id = tdocs1.scoreDocs[i1].doc;
            String idd = reader.document(id).get("id");
            if (firstModelDocIds.contains(idd)) {
                index = firstModelDocIds.indexOf(idd);
                double diff = index - i1;
                if (diff < 0) {
                    diff = -diff;
                }
                if (diff >= 0) {
                    if(maxDiff < diff)
                    {
                        maxDiff = diff;
                        id = i1;
                    }
                    double diff1 = (tdocs.scoreDocs[0].score - tdocs.scoreDocs[index].score) / tdocs.scoreDocs[0].score;
                    double diff2 = (tdocs1.scoreDocs[0].score - tdocs1.scoreDocs[i1].score) / tdocs1.scoreDocs[0].score;
                    diff = diff1 - diff2;
                    System.out.println(diff + " " + diff1 + " " + diff2 + " " + index + " " + i + " " + id);
                }
            }
        }
        Document doc = reader.document(id);
        
        HashSet<String> queryWords = new HashSet<>();
        FeatureSpace fs = new FeatureSpace();
        fs = fs.computeAvfFeatureValue(queryWords,id, reader);

        System.out.println(fs.tf+ "\t" + fs.docLength + "\t" + fs.df);
       
    }

  
    

    public void computeAvgFiedelity(String query, String fidelityWriteFile, int i, int j,double[] coeff) throws Exception {
        Query q = buildQuery(query);
        HashSet<String> querywords = (HashSet<String>) getWordCOuntMap(analyze(query)).keySet();
        TopDocs tdocs = searcher.search(q, topk);

        FileWriter fw = new FileWriter(new File(fidelityWriteFile));
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write("i\tj\tdeltax\tdeltay\tdeltaz");
        bw.newLine();
        
        double posneg[] = new double[coeff.length * 2];
        for (int i1 = 0; i1 < i; i1++) {
            FeatureSpace f1 = new FeatureSpace();
            f1=f1.computeAvfFeatureValue(querywords, i1, reader);
            EvaluationMetric ev = new EvaluationMetric(f1, coeff);
            for (int j1 = i + 1; j1 < j; j1++) {
                FeatureSpace f2 =new FeatureSpace();
                f2=f2.computeAvfFeatureValue(querywords, j1, reader);
                ev.computeFidelityScore(f2);
                bw.write(String.valueOf(i1) + "\t" + String.valueOf(j1) + "\t" + String.valueOf(ev.fidelityScore[0]) + "\t" + String.valueOf(ev.fidelityScore[1]) + "\t" + String.valueOf(ev.fidelityScore[2]));
                bw.newLine();
            }
            for (int j1 = 0; j1 < 2 * coeff.length; j1++) {
                posneg[j1] += ev.posnegCount[j1];
            }
        }
        bw.close();
        System.out.println(posneg[0] + " " + posneg[1]);
    }

    public static void main(String[] args) throws IOException, Exception {
        RankingModelRegression qs = new RankingModelRegression(args[0]);
    }
}
