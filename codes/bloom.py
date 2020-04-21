#source: https://www.geeksforgeeks.org/bloom-filters-introduction-and-python-implementation/
# pip install mmh3
# pip install bitarray
import math
import mmh3
from bitarray import bitarray

class BloomFilter(object):
    """Filtro de Bloom, es necesario usar la función hash: murmur3"""
    def __init__(self, items_count,fp_prob):
        """
        items_count : número de elementos esperados a ser almacenados en el filtro
        fp_prob : probabilidad de falso positivo (en decimales)
        """
        self.fp_prob = fp_prob #probalilidad de falsos positivos
        self.size = self.get_size(items_count,fp_prob) #tamaño del array en bits
        self.hash_count = self.get_hash_count(self.size,items_count) #número de funciones hash
        self.bit_array = bitarray(self.size)#se cre el array de acuerdo al tamaño indicado
        self.bit_array.setall(0) #se inicializan todos los bits en 0s

    def add(self, item):
        """ Función que añade un elemento al filtro """
        digests = []
        for i in range(self.hash_count):
            digest = mmh3.hash(item,i) % self.size #'i' sirve como semilla para la función hash
            digests.append(digest)
            self.bit_array[digest] = True #se configura el bit a 'True' o '1' en bit_array

    def check(self, item):
        """Función para checar si un elemento existe en el filtro"""
        for i in range(self.hash_count):
            digest = mmh3.hash(item,i) % self.size
            if self.bit_array[digest] == False: #si alguno de los bits es no existe, entonces no está presente en el filtro
                                                #en caso contrario, hay una probabilidad de que si exista
                return False
        return True

    @classmethod
    def get_size(self,n,p):
        """ Retorna el tamaño del array(m) y se usa en la siguiente fórmula
        m = -(n * lg(p)) / (lg(2)^2)
        n : número de elementos a almacenar
        p : probabilidad de falsos positivos
        """
        m = -(n * math.log(p))/(math.log(2)**2)
        return int(m)

    @classmethod
    def get_hash_count(self, m, n):
        """ Retorna la función hash (k) y se usa en la siguiente fórmula
        k = (m/n) * lg(2)
        m = tamaño de bits en el array
        n : número de elementos a almacenar
        """
        k = (m/n) * math.log(2)
        return int(k)
