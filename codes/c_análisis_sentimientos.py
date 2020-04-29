from pandas import read_csv
import pandas as pd
import numpy as np
import re
from functools import reduce
from collections import Counter
import seaborn as sns; sns.set(style="ticks", color_codes=True)
import matplotlib.pyplot as plt
import collections
from itertools import islice
import unicodedata
import nltk
from textblob import TextBlob

raw_tweets = '../data/export.csv' #Ubicación de los tweets descargados de Databricks!

def read_data(raw_tweet):
    print("Cargando tweets")
    data = read_csv(raw_tweet, header=0,na_filter=True, encoding= 'utf-8')
    return data

def clean_text(tweets):
    texto = []
    for sen in range (0,len(tweets)):
        tweet = re.sub(r'[0-9]','', str(tweets[sen])) #elimina números del 0 al 9
        tweet = tweet.lower()# Converting to Lowercase
        tweet = ''.join((c for c in unicodedata.normalize('NFD',tweet) if unicodedata.category(c) != 'Mn'))#eliminamos acentos
        tweet = re.sub(r'https?://\S+', '', tweet) #eliminamos URLs
        tweet = re.sub(r"#(\w+)",'',tweet) #eliminamos los hashtags del texto
        tweet = re.sub(r"@(\w+)",'',tweet) #eliminamos la mención de usuarios
        tweet = re.sub(r'\s+[a-zA-Z]\s+', ' ', tweet) # remove all single characters
        tweet = re.sub(r'\^[a-zA-Z]\s+', ' ', tweet)# Remove single characters from the start
        tweet = re.sub(r'^b\s+', ' ', tweet)# Removing prefixed 'b
        tweet = tweet.replace('rt','')
        tweet = ''.join((c for c in unicodedata.normalize('NFD',tweet) if unicodedata.category(c) != 'Mn'))
        tweet = re.sub(r'\s+', ' ', tweet, flags=re.I)# Sustituimos múltiples espacios con un espacio
        texto.append(tweet)
    texto = list(filter(None,texto))#podríamos tener filas en blanco en la lista
    return texto

def analysis_sentimientos(tweets):
    neutral_threshold = 0.6
    texto_tweet = []
    polaridad_tweet = []
    listToStr = ' '.join(map(str, tweets)) #convertimos de list a string
    blob = TextBlob(listToStr)
    blob.tags           # [('The', 'DT'), ('titular', 'JJ'),
                        #  ('threat', 'NN'), ('of', 'IN'), ...]
    blob.noun_phrases   # WordList(['titular threat', 'blob',
                        #            'ultimate movie monster',
                        #            'amoeba-like mass', ...])
    for sentence in blob.sentences:
        polaridad = sentence.sentiment.polarity
        if polaridad >= neutral_threshold:
            p = 1
        elif polaridad > 0.5 and polaridad < neutral_threshold:
            p = 0.5
        else:
            p = 0
        polaridad_tweet.append([sentence, p]) #guardamos la sentencia y su polaridad
    return polaridad_tweet, texto_tweet

if __name__ == "__main__":
    raw_tweet = read_data(raw_tweets)
    tweets = raw_tweet['text']
    tweets_clean = clean_text(tweets)
    polaridad_tweet = analysis_sentimientos(tweets_clean) #retorna el resultado de polaridad
    polaridad_tweets = reduce(lambda x,y: x+y,polaridad_tweet) #debido a que 'polaridad_tweet' es una lista de lista, generamos una lista plana
    for x in range(len(polaridad_tweets)): #recorremos la lista e imprimimos el tweet y su polaridad
        print (polaridad_tweets[x])
