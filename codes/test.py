from bloom import BloomFilter
from random import shuffle

n = 20 #número de elementos a añadir
p = 0.05 #probabilidad de falsos positivos

bloom_filtro = BloomFilter(n,p)
print("Tamaño del array:{}".format(bloom_filtro.size))
print("Probabilidad de falsos positivos:{}".format(bloom_filtro.fp_prob))
print("Número de funciones hash:{}".format(bloom_filtro.hash_count))

# Nombres de usuarios a ser añadidos
nombres_existentes = ['ironman','thor','american_captain','spiderman','loki',
                'wolverine','black_widow','hulk','deadpool','nick_fury','thanos',
                'Dr_strange','venon','odin','magneto','black_panter',
                'rocket','gamora','ultron','groot','ant_man']

# Palabras no existentes
nombres_no_existentes = ['superman','batman','wonder_woman','green_Lantern','he_Man',
               'batgirl','lion_O','shazam','aquaman','green_arrow',
               'flash','tygro','Cheetara']

for item in nombres_existentes:
    bloom_filtro.add(item)

shuffle(nombres_existentes)
shuffle(nombres_no_existentes)

prueba = nombres_existentes[:10] + nombres_no_existentes
shuffle(prueba)
print("=======================================================")
for word in prueba:
    if bloom_filtro.check(word):
        if word in nombres_no_existentes:
            print("'{}' es un falso positivo!".format(word))
        else:
            print("'{}' probablemente existe!".format(word))
    else:
        print("'{}' definitivamente no existe!".format(word))
print("=======================================================")
