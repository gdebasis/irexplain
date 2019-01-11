from __future__ import unicode_literals

import sys
import lucene
import numpy as np
import math

from java.io import File
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
import itertools
import json
import re
import scipy as sp
import sklearn
from sklearn.utils import check_random_state

from . import explanation
from . import lime_base


class TextDomainMapper(explanation.DomainMapper):
    """Maps feature ids to words or word-positions"""

    def __init__(self, doc_vector):
        """Initializer.

        Args:
            indexed_string: original dictionary
        """
        self.indexed_string = doc_vector
        self.feature_names = list(doc_vector.keys())
        self.feature_values = list(doc_vector.values())

    def map_exp_ids(self, exp):
        """Maps ids to words or word-position strings.

        Args:
            exp: list of tuples [(id, weight), (id,weight)]

        Returns:
            list of tuples (word, weight) 
            examples: ('bad', 1) or ('bad_3-6-12', 1)
        """
        print(exp)
        names = self.feature_names
        exp = [(names[x[0]], x[1]) for x in exp]
        return exp

    '''
    def visualize_instance_html(self, exp, label, div_name, exp_object_name,
                                text=True, opacity=True):
        """Adds text with highlighted words to visualization.

        Args:
             exp: list of tuples [(id, weight), (id,weight)]
             label: label id (integer)
             div_name: name of div object to be used for rendering(in js)
             exp_object_name: name of js explanation object
             text: if False, return empty
             opacity: if True, fade colors according to weight
        """
        raw_string = ' '.join(self.indexed_string.keys())
        keys = self.indexed_string.keys()
        if not text:
            return u''
        text = (raw_string.encode('utf-8', 'xmlcharrefreplace').decode('utf-8'))
        text = re.sub(r'[<>&]', '|', text)
        exp = [(x[0], keys.index(x[0]), x[1]) for x in exp]
        all_occurrences = list(itertools.chain.from_iterable(
            [itertools.product([x[0]], x[1], [x[2]]) for x in exp]))
        all_occurrences = [(x[0], int(x[1]), x[2]) for x in all_occurrences]
        ret = '%s.show_raw_text(%s, %d, %s, %s, %s);
             '% (exp_object_name, json.dumps(all_occurrences), label,
                   json.dumps(text), div_name, json.dumps(opacity))
        return ret
    '''
    def visualize_instance_html(self,
                                exp,
                                label,
                                div_name,
                                exp_object_name,
                                show_table=True,
                                show_all=False):
        """Shows the current example in a table format.

        Args:
             exp: list of tuples [(id, weight), (id,weight)]
             label: label id (integer)
             div_name: name of div object to be used for rendering(in js)
             exp_object_name: name of js explanation object
             show_table: if False, don't show table visualization.
             show_all: if True, show zero-weighted features in the table.
        """
        if not show_table:
            return ''
        weights = [0] * len(self.feature_names)
        for x in exp:
            weights[x[0]] = x[1]
        out_list = list(zip(self.feature_names,
                            self.feature_values,
                            weights))
        if not show_all:
            out_list = [out_list[x[0]] for x in exp]
        ret = u'''
            %s.show_raw_tabular(%s, %d, %s);
        ''' % (exp_object_name, json.dumps(out_list, ensure_ascii=False), label, div_name)
        return ret



class LimeRankerExplainer(object):
    """Explains document rankers.
       The overall implementation of a ranker explanation is as follows:
       Required: Query, document
       Result: Explanation of the rank of the document with respect to a query.
       Algorithm:
            1. Sample keywords from the document to form pseudo document
            2. Find the score of pseudo documents and train a model on the scores.

        The index is assumed to have the following structure. Each document is 
        indexed with an id and words field. 
    """
    index_dir = None
    rel_labels = None
    reader = None
    searcher = None

    def __init__(self, kernel_width=None, kernel=None, verbose=False,\
                 relevance_labels=None, lucene_index_path=None, \
                 random_state=None, feature_selection='auto'):
        '''
        Args:
            kernel_width: kernel width for the exponential kernel.
            kernel: similarity kernel that takes euclidean distances and kernel
                width as input and outputs weights in (0,1). If None, defaults to
                an exponential kernel.
            verbose: if true, print local prediction values from linear model
            relevance_labels: list of relevance labels ordered according to whatever the
                ranker is using.
            feature_selection: feature selection method. can be
                'forward_selection', 'lasso_path', 'none' or 'auto'.
                See function 'explain_instance_with_data' in lime_base.py for
                details on what each of the options does.
            lucene_index_path: path to index documents.
            random_state: an integer or numpy.RandomState that will be used to
                generate random numbers. If None, the random state will be
                initialized using the internal numpy seed.
            feature_selection: feature selection method. can be
                'forward_selection', 'lasso_path', 'none' or 'auto'.
                See function 'explain_instance_with_data' in lime_base.py for
                details on what each of the options does.

            '''
        self.rel_labels = relevance_labels
        lucene.initVM(classpath=lucene.CLASSPATH, vmargs=['-Djava.awt.headless=true'])
        self.analyzer = StandardAnalyzer()
        index_path = File(lucene_index_path).toPath()
        self.index_dir = FSDirectory.open(index_path)
        self.reader = DirectoryReader.open(self.index_dir)
        self.searcher = IndexSearcher(self.reader)
        self.feature_selection = feature_selection
        self.random_state = check_random_state(random_state)
        
        if kernel_width is None:
            kernel_width = np.sqrt(10000) * .75
        kernel_width = float(kernel_width)

        if kernel is None:
            def kernel(d, kernel_width):
                return np.sqrt(np.exp(-(d ** 2) / kernel_width ** 2))

        kernel_fn = partial(kernel, kernel_width=kernel_width)

        self.base = lime_base.LimeBase(kernel_fn, verbose, \
                                       random_state=self.random_state)
        

    def get_document_vector(self, document_id, id_field, text_field): 

        ''' 
            Given a document id, fetch the tf-idf vector of the document.
        '''

        tc_dict = {}                     # Counts of each term
        dc_dict = {}                     # Number of docs associated with each term
        tfidf_dict = {}                  # TF-IDF values of each term in the doc
        # Get the document id.
        query_parser = QueryParser(id_field, WhitespaceAnalyzer() )
        score_docs = self.searcher.search(query_parser.parse(str(document_id)),1).scoreDocs
        if len(score_docs) > 0:
            # get the tf-idf vector.
            termVector = self.reader.getTermVector(score_docs[0].doc, text_field);
            termsEnumvar = termVector.iterator()
            termsref = BytesRefIterator.cast_(termsEnumvar)
            N_terms = 0
            try:
                while (termsref.next()):
                    termval = TermsEnum.cast_(termsref)
                    fg = termval.term().utf8ToString()       # Term in unicode
                    if len(fg) > 4 and not fg.isdigit():
                        tc = termval.totalTermFreq()             # Term count in the doc

                        # Number of docs having this term in the index
                        dc = self.reader.docFreq(Term(text_field, termval.term())) 
                        N_terms = N_terms + 1 
                        tc_dict[fg]=tc
                        dc_dict[fg]=dc
            except:
                print('error in term_dict')

            # Compute TF-IDF for each term
            for term in tc_dict:
                tf = tc_dict[term] / N_terms
                idf = 1 + math.log(self.reader.numDocs()/(dc_dict[term]+1)) 
                tfidf_dict[term] = tf*idf

        return tfidf_dict


    def _data_labels_distance(self, samples, tfidf_dict, distance_metric='cosine'):

        """Calculates the distance between different samples for a document. 
        Uses cosine distance to compute distances between original and \
        perturbed instances.
        Args:
            samples: array of strings
            tfidf_dict: document dict of tf-idf
            num_samples: size of the neighborhood to learn the linear model
            distance_metric: the distance metric to use for sample weighting,
                defaults to cosine similarity.


        Returns:
            distances: cosine distance between the original instance and
            each perturbed instance (computed in the binary 'data'
            matrix), times 100.
        """
        
        def distance_fn(x):
            return sklearn.metrics.pairwise.pairwise_distances(
                x, x[0], metric=distance_metric).ravel() * 100

        base_doc_vector = np.fromiter(tfidf_dict.values(),float)
        base_doc_keys = list(tfidf_dict.keys())
        vectors = [base_doc_vector]

        for sample in samples:
            missing_words = []
            sample_vector = np.zeros(len(base_doc_keys))
            for token in sample.split():
                try:
                    token_index = base_doc_keys.index(token)
                    sample_vector[token_index] = base_doc_vector[token_index]
                except Exception as ex:
                    missing_words.append(token)
            if len(missing_words) > 0:
                print(len(missing_words), ' missing words', missing_words,\
                 ' from Sample', sample)
            vectors.append(sample_vector)

        distances = distance_fn(sp.sparse.csr_matrix(vectors))
        return np.array(vectors), distances


    def explain_document_label(self, document_id, \
                               samples, samples_scores,num_features=10):

        document_dict = self.get_document_vector(document_id,\
                                                    'id','words')
        print(document_dict.keys())
        # TEMP FIX.
        document_score = 8.3 #np.median(samples_scores)
        sample_vectors, distances = self._data_labels_distance(samples, document_dict)
        domain_mapper = TextDomainMapper(document_dict)
        ret_exp = explanation.Explanation(domain_mapper=domain_mapper,
                                          mode='regression',
                                          class_names='document_score',
                                          random_state=self.random_state)

        ret_exp.predicted_value = document_score
        ret_exp.min_value = min(samples_scores)
        ret_exp.max_value = max(samples_scores)
        labels = [0]
        yss = np.array([document_score] + samples_scores)
        yss = yss[:, np.newaxis]
        print(distances.shape, yss.shape, sample_vectors.shape)
        print(distances[:5])
        print(yss)
        print(sample_vectors[:5])
        for label in labels:
            (ret_exp.intercept[label],
             ret_exp.local_exp[label],
             ret_exp.score, ret_exp.local_pred) = self.base.explain_instance_with_data(
                sample_vectors, yss ,\
                 distances, label, num_features,
                model_regressor=None,
                feature_selection=self.feature_selection)

  
        ret_exp.intercept[1] = ret_exp.intercept[0]
        ret_exp.local_exp[1] = [x for x in ret_exp.local_exp[0]]
        ret_exp.local_exp[0] = [(i, -1 * j) for i, j in ret_exp.local_exp[1]]
        return ret_exp

   
    