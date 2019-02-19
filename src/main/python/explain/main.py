import lucene
from org.apache.lucene.document import Document, Field
from org.apache.lucene.search import IndexSearcher, Explanation
from org.apache.lucene.search.similarities import TFIDFSimilarity, LMJelinekMercerSimilarity;
from org.apache.lucene.index import IndexReader,DirectoryReader,TermsEnum,Term
from org.apache.lucene.queryparser.classic import QueryParser
from org.apache.lucene.store import SimpleFSDirectory, FSDirectory
from org.apache.lucene.util import Version, BytesRefIterator
from org.apache.lucene.analysis.standard import StandardAnalyzer
from org.apache.lucene.analysis.core import WhitespaceAnalyzer 
from org.apache.lucene.queryparser.flexible.standard import StandardQueryParser
from sklearn.utils import check_random_state
from functools import partial
import numpy as np
from java.io import File
from org.apache.lucene.analysis.en import EnglishAnalyzer
import lime
from lime import lime_ranker
import re
import matplotlib.pyplot as plt
from scipy.stats import kendalltau
import pandas as pd
from plotly.offline import init_notebook_mode,iplot, plot
import plotly.graph_objs as go
import plotly.plotly as py
from IPython.display import display
from scipy.stats import entropy
from sklearn.metrics.pairwise import cosine_similarity
import multiprocessing 
import math
import operator



class QRel:
    def __init__(self):
        self.query_label = {}
        self.doc_label = {}
    
    def set_rel(self, query_id, doc_id, rel_label):
        if query_id not in self.query_label:
            self.query_label[query_id] = {}
        
        self.query_label[query_id][doc_id] = rel_label
        
        if doc_id not in self.doc_label:
            self.doc_label[doc_id] = {}
        
        self.doc_label[doc_id][query_id] = rel_label
        
    def get_rel(self, query_id, doc_id):
        try:
            return self.query_label[query_id][doc_id]
        except Exception as ex:
            print(ex)
        return -1



def load_qrels(qrel_path):
    qrel = {}
    with open(qrel_path,'r') as ifile:
        for line in ifile:
            split = line.split()
    


def load_samples(file_path):
    samples_list = {}
    with open(file_path, 'r') as ifile:
        for line in ifile:
            split = line.split('\t')
            doc_id = split[1][:split[1].rindex('_')]
            try:
                index_key = (split[0],doc_id, float(split[5]))
                if index_key not in samples_list:
                    samples_list[index_key] = []
                samples_list[index_key].append({ 'sample_id':split[1], \
                                               'sample_text': tokenize_text(split[3].strip()),\
                                               'sample_score': float(split[4])})
            except:
                print(split)
    return samples_list

#[\\\\/:*?"<>|]

def tokenize_text(text):
    # remove symbols
    ntext = re.sub(r'\W+', ' ', text)
    analyzer = EnglishAnalyzer()
    parser = StandardQueryParser(analyzer)
    parsed_text = parser.parse(ntext,'').toString('')
    parsed_text = re.sub('[)()]', '', parsed_text)
    return parsed_text


def consistancy(explanation_objects):
    '''
        Compares the relative differences in explanations for the same document accross different sampling 
    '''
    #kendall_values = {}
    scores = []
    for i in range(len(explanation_objects)):
        #kendall_values[i] = {}
        for j in range(i+1,len(explanation_objects)):
            kscore = kendalltau(explanation_objects[i],explanation_objects[j])
            if kscore[1] < 0.05:
                #kendall_values[i][j] = kscore[0] 
                scores.append(kscore[0])
    return np.mean(scores)



def divergence_from_truth(rel_vector, non_rel_vector,  explain_vector):
    
    
    # find the common words in all three vectors.    
    pos_all_vect = pd.DataFrame({'rel_vector': rel_vector, 
                  'explain_vector': explain_vector}).fillna(0.0001)
    neg_all_vect = pd.DataFrame({'non_rel_vector':non_rel_vector,\
                  'explain_vector': explain_vector}).fillna(0.0001)
    
    
    #print(all_vectors[all_vectors['explain_vector'] > 0].head())

   	#norm_df = all_vectors.apply(lambda x: x/x.max(), axis=0)
    pos_ent = entropy(pos_all_vect['rel_vector'].values,pos_all_vect['explain_vector'].values )
    neg_ent = entropy(neg_all_vect['non_rel_vector'].values,neg_all_vect['explain_vector'].values )
    #print(neg_ent, pos_ent, all_vectors.shape)
    
    '''
    # Compute KL Divergence between relevant and non-relevant    
    neg_ent =  cosine_similarity([all_vectors['non_rel_vector'].values],\
          [all_vectors['explain_vector'].values])
    pos_ent =  cosine_similarity([all_vectors['rel_vector'].values], \
                                 [all_vectors['explain_vector'].values])
    #print(neg_ent, pos_ent, len(explain_vector), all_vectors.shape)
    '''
    
    return  neg_ent, pos_ent, neg_ent/pos_ent
    
def divergence_from_truth_cosine(rel_vector, non_rel_vector,  explain_vector):
    
    
    # find the common words in all three vectors.    
    pos_all_vect = pd.DataFrame({'rel_vector': rel_vector, 
                  'explain_vector': explain_vector}).fillna(0.0001)
    neg_all_vect = pd.DataFrame({'non_rel_vector':non_rel_vector,\
                  'explain_vector': explain_vector}).fillna(0.0001)
    
        

    # Compute Cosine between relevant and non-relevant    
    neg_ent =  cosine_similarity([neg_all_vect['non_rel_vector'].values],\
          [neg_all_vect['explain_vector'].values])
    pos_ent =  cosine_similarity([pos_all_vect['rel_vector'].values], \
                                 [pos_all_vect['explain_vector'].values])
    
    return  neg_ent, pos_ent, (1.0- neg_ent)/(1.0 - pos_ent)


    
def compute_query_document_vectors(qrel_path, searcher, reader):
    # Read the qrel file
    qrel_object = QRel()
    
    query_vectors = {}
    doc_counts = {}
    for line in open(qrel_path, 'r'):
        split = line.split(' ')
        query_id = split[0]
        doc_id = split[2]
        rel_label = int(split[3])
        if query_id not in query_vectors:
            query_vectors[query_id] = {}
            doc_counts[query_id] = {}
        
        if rel_label not in query_vectors[query_id]:
            query_vectors[query_id][rel_label] = {}
            doc_counts[query_id][rel_label] = 0.0
            
        qrel_object.set_rel(query_id, doc_id, rel_label)
        
        if doc_counts[query_id][rel_label] < 50:
            doc_vector = get_document_vector(searcher, reader, doc_id,'id',\
                                             'words')
       
            for entry , value in doc_vector.items():
                if entry not in query_vectors[query_id][rel_label]:
                    query_vectors[query_id][rel_label][entry] = value
                else:
                    query_vectors[query_id][rel_label][entry] += value
                
            doc_counts[query_id][rel_label] += 1.0
        
    # normalize the vectors.
    for query_id in query_vectors.keys():
        for rel_label in query_vectors[query_id].keys():
            for word in query_vectors[query_id][rel_label].keys():
                query_vectors[query_id][rel_label][word]/=doc_counts[query_id][rel_label]
        
 
    return qrel_object, query_vectors


def compute_document_vectors(samples, searcher, reader):

	document_vectors = {}
	for index_key, sample_list in samples.items():
		document_vectors[index_key[1]] = get_document_vector(searcher, reader, \
		                                                     index_key[1],\
		                                                     'id','words')
	print('Finished document vector computation.')
	return document_vectors 


def compute_metrics(samples, qrel_object, query_vectors, kernel_range, \
                    document_vectors, ranker_explanation, top_k, name):
    consistancy_scores=[]
    divergence_scores=[]
    query_count = {}
    for index_key, sample_list in samples.items():
        # index_key --> query_id, doc_id, doc_score
        query_id = index_key[0].strip()
        doc_id = index_key[1]
        if query_id not in query_count:
            print('Evaluating for query', query_id, top_k)
            query_count[query_id] = 0
        query_count[query_id] +=1
        
        sample_scores = [x['sample_score'] for x in sample_list]
        sample_texts =  [(x['sample_text']) for x in sample_list]

        if len(sample_list) > 100:
        	idx = np.random.choice(np.arange(len(sample_list)), 100, replace=False)
        else:
        	idx = np.arange(len(sample_list))

        document_dict = document_vectors[doc_id]
        doc_label = qrel_object.get_rel(query_id, doc_id)
        word_list = list(document_dict.keys())
        
        explain_objects = ranker_explanation.explain_document_label(document_dict,\
        													index_key[2],\
        													list(operator.itemgetter(*idx)(sample_texts)),\
        													list(operator.itemgetter(*idx)(sample_scores)),\
                                                            top_k,\
                                                            weights_range=kernel_range)
        ranked_lists = []
        for eobject, kernel in zip(explain_objects, kernel_range):

            ranked_lists.append([entry[0] for entry in sorted(eobject.local_exp[1],\
                                                              key = lambda x: x[1],\
                                                              reverse=True)])
            ne, pe, dfr = divergence_from_truth(query_vectors[query_id][1],\
                                                query_vectors[query_id][0],\
                                                explanation_to_vector(word_list,\
                                                                      eobject.local_exp[1],\
                                                document_dict))
            cne, cpe, cdfr = divergence_from_truth_cosine(query_vectors[query_id][1],\
			                                              query_vectors[query_id][0],\
			                                              explanation_to_vector(word_list,\
			                                                                    eobject.local_exp[1],\
			                                                                    document_dict))
            divergence_scores.append({'doc_id':doc_id,'top_feat':top_k,\
            						  'kernel':kernel, \
                                      'doc_rel':doc_label ,\
                                      'doc_score':index_key[2], \
                                      'dfr': dfr, 'ne': ne, 'pe':pe, \
                                      'cdfr': cdfr, 'cne': cne, 'cpe':cpe, \
                                      'query_id': query_id} )

                
        consistancy_scores.append({'doc_id':doc_id, 'query_id': query_id,\
                                   'ktau': consistancy(ranked_lists),\
                                   'doc_score':index_key[2],'top_feat':top_k,\
                                   'doc_rel':doc_label})

     

    cfrane = pd.DataFrame(consistancy_scores)
    cfrane.to_csv(name+'_kendal_score'+str(top_k)+'.csv', sep=',', \
                  index=False)

    dfrane = pd.DataFrame(divergence_scores)
    dfrane.to_csv(name+'_divergence_scores'+str(top_k)+'.csv', sep=',', \
                  index=False)



def compute_metrics_with_uniform_kernel(sample_size, niter, all_samples, \
                                        qrel_object, query_vectors, document_vectors,\
                                        ranker_explanation, top_k, name):
    consistancy_scores=[]
    divergence_scores=[]
    query_count = {}

    # sample documents
    for index_key, sample_list in all_samples.items():
        # index_key --> query_id, doc_id, doc_score
        query_id = index_key[0].strip()
        doc_id = index_key[1]
        if query_id not in query_count:
            print('Evaluating for query', query_id, top_k)
            query_count[query_id] = 0
        query_count[query_id] +=1
        sample_scores = [x['sample_score'] for x in sample_list]
        sample_texts =  [(x['sample_text']) for x in sample_list]
        document_dict = document_vectors[doc_id]
        doc_label = qrel_object.get_rel(query_id, doc_id)
        word_list = list(document_dict.keys())
        ranked_lists = []

        for i in range(niter):
        	if len(sample_list) > sample_size:
        		idx = np.random.choice(np.arange(len(sample_list)), sample_size, replace=False)
        	else:
        		idx = np.arange(len(sample_list))

        	
        	explain_objects = ranker_explanation.explain_document_label(document_dict,\
        													index_key[2],\
        													list(operator.itemgetter(*idx)(sample_texts)),\
        													list(operator.itemgetter(*idx)(sample_scores)),\
                                                            top_k,weights_range = None )
        	for eobject in explain_objects:
        		ranked_lists.append([entry[0] for entry in sorted(eobject.local_exp[1],\
                                                              key = lambda x: x[1],\
                                                              reverse=True)])

        		ne, pe, dfr = divergence_from_truth(query_vectors[query_id][1],\
                                                query_vectors[query_id][0],\
                                                explanation_to_vector(word_list,\
                                                                      eobject.local_exp[1],\
                                                document_dict))
        		cne, cpe, cdfr = divergence_from_truth_cosine(query_vectors[query_id][1],\
			                                              query_vectors[query_id][0],\
			                                              explanation_to_vector(word_list,\
			                                                                    eobject.local_exp[1],\
			                                                                    document_dict))
        		divergence_scores.append({'doc_id':doc_id,'top_feat':top_k,\
            						  'kernel':i, \
                                      'doc_rel':doc_label ,\
                                      'doc_score':index_key[2], \
                                      'dfr': dfr, 'ne': ne, 'pe':pe, \
                                      'cdfr': cdfr, 'cne': cne, 'cpe':cpe, \
                                      'query_id': query_id} )

        consistancy_scores.append({'doc_id':doc_id, 'query_id': query_id,\
                                   'ktau': consistancy(ranked_lists),\
                                   'doc_score':index_key[2],'top_feat':top_k,\
                                   'doc_rel':doc_label})
    cfrane = pd.DataFrame(consistancy_scores)
    cfrane.to_csv(name+'_kendal_score'+str(top_k)+'.csv', sep=',', index=False)
    dfrane = pd.DataFrame(divergence_scores)
    dfrane.to_csv(name+'_divergence_scores'+str(top_k)+'.csv', sep=',', index=False)
	

def get_document_vector(searcher, reader, document_id, \
                        id_field, text_field): 

    ''' 
        Given a document id, fetch the tf-idf vector of the document.
    '''
    tc_dict = {}                     # Counts of each term
    dc_dict = {}                     # Number of docs associated with each term
    tfidf_dict = {}                  # TF-IDF values of each term in the doc
    # Get the document id.
    query_parser = QueryParser(id_field, WhitespaceAnalyzer() )
    score_docs = searcher.search(query_parser.parse(str(document_id)),1).scoreDocs
    if len(score_docs) > 0:
        # get the tf-idf vector.
        termVector = reader.getTermVector(score_docs[0].doc, text_field);
        termsEnumvar = termVector.iterator()
        termsref = BytesRefIterator.cast_(termsEnumvar)
        N_terms = 0
        try:
            while (termsref.next()):
                termval = TermsEnum.cast_(termsref)
                fg = termval.term().utf8ToString()    # Term in unicode
                if len(fg) > 3 and not fg.isdigit():
                    tc = termval.totalTermFreq()      # Term count in the doc

                    # Number of docs having this term in the index
                    dc = reader.docFreq(Term(text_field, termval.term())) 
                    N_terms = N_terms + 1 
                    tc_dict[fg]=tc
                    dc_dict[fg]=dc
        except:
            print('error in term_dict')

        # Compute TF-IDF for each term
        for term in tc_dict:
            tf = tc_dict[term] / N_terms
            idf = 1 + math.log(reader.numDocs()/(dc_dict[term]+1)) 
            tfidf_dict[term] = tf*idf

    return tfidf_dict



def explanation_to_vector(word_list, explain_tuples, doc_vector):
    #print(explain_tuples)
    return dict([ (word_list[entry[0]] ,\
     doc_vector[word_list[entry[0]]]) for entry in explain_tuples])

def uniform_kernel(d, kernel_width):
    return d


def main():
	lucene_index_path = '/Users/manishav/workspace/irexplain/data/trec/index_old/'
	#samples_path = '/Users/manishav/workspace/irexplain/samples/200_sized_samples/samples_0._tfidf.txt'
	#samples_path = '/Users/manishav/workspace/irexplain/samples/pointwise/uniform/mask/samples_mask.ws.10.0.1.txt'
	dir_path='/Users/manishav/workspace/irexplain/'

	sample_files=[dir_path+'samples/200_sized_samples/samples_0.3_tfidf.txt']
	#dir_path+'samples/pointwise/uniform/mask/samples_mask.ws.10.0.3.txt',\
	#dir_path+'samples/pointwise/uniform/mask/samples_mask.ws.20.0.3.txt',\
	#dir_path+'samples/pointwise/term_weight/samples_mask.ws.5.0.3.txt',\
	#dir_path+'samples/pointwise/term_weight/samples_mask.ws.10.0.3.txt',\
	#dir_path+'samples/pointwise/term_weight/samples_mask.ws.20.0.3.txt']

	qrel_path = '/Users/manishav/workspace/irexplain/data/trec/qrels/qrels.trec8.adhoc'

	env = lucene.initVM(classpath=lucene.CLASSPATH, vmargs=['-Djava.awt.headless=true'])
	index_path = File(lucene_index_path).toPath()
	index_dir = FSDirectory.open(index_path)
	reader = DirectoryReader.open(index_dir)
	searcher = IndexSearcher(reader)
	qrel_object, query_vectors = compute_query_document_vectors(qrel_path, \
	                                                            searcher, reader)

	#kernel=uniform_kernel,\
	ranker_explanation = lime_ranker.LimeRankerExplainer(kernel=uniform_kernel,\
	                                      kernel_width = np.sqrt(100000) * .80,\
                                          relevance_labels=[0,1,2,3,4])


	for ifile in sample_files:
		print(ifile)
		fname = ifile[ifile.rfind('_samples')+9:ifile.rfind('.')].replace('/','_')
		samples = load_samples(ifile)
		doc_vectors = compute_document_vectors(samples, searcher, reader)
		#kernel_range = np.outer(np.logspace(-2,5,num=8,base=10,dtype='float'),\
		#				 	np.linspace(0.1, 1.0, 10)).ravel()
		kernel_range = [1]
		'''
	
		for entry in [10,20]:
			p = multiprocessing.Process(target=compute_metrics, \
		                            args=(samples, qrel_object, query_vectors, \
		                                  kernel_range, doc_vectors, \
		                                  ranker_explanation, entry))
			p.start()
		'''
		for entry in [10,15,20,25,30]:
			#compute_metrics(samples, qrel_object, query_vectors, \
		    #                              kernel_range, doc_vectors, \
		    #                              ranker_explanation, entry,\
		    #                              fname)	

			compute_metrics_with_uniform_kernel(70, 30, samples, qrel_object,\
										 query_vectors, doc_vectors, \
		                                 ranker_explanation, entry,\
		                                 fname)	
	
	

if __name__ == '__main__':
	main()


    