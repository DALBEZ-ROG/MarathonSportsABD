#!/usr/bin/perl
# =============================================================================
# Escribe el nombre en cada @RequestParam / @PathVariable — Fase 94
# -----------------------------------------------------------------------------
# POR QUE HACE FALTA
# Spring deduce el nombre de un parametro web del nombre del argumento de Java, y
# eso solo esta en el .class si se compilo con -parameters. Maven lo pone
# (spring-boot-starter-parent), pero el compilador del IDE escribe en el MISMO
# target/classes sin ese flag; cuando lo hace, Maven da la clase por actualizada
# y no la rehace, y la aplicacion arranca con una clase sin nombres.
#
# El resultado es un 500 en la pantalla entera con el mensaje
# «Name for argument of type [int] not specified», que no menciona ni al IDE ni a
# Maven y manda a buscar el fallo donde no esta. Paso dos veces en un mismo dia.
#
# Escribiendo el nombre en la anotacion, da igual quien compile.
#
# USO:  perl nombrar_parametros.pl <fichero.java> [...]
#       perl nombrar_parametros.pl --revisar <fichero.java>   (no escribe nada)
# =============================================================================
use strict;
use warnings;

my $revisar = 0;
if (@ARGV && $ARGV[0] eq '--revisar') { $revisar = 1; shift @ARGV; }

my $tocados = 0;
my $cambios = 0;

for my $arch (@ARGV) {
    open(my $fh, '<:encoding(UTF-8)', $arch) or die "No se pudo abrir $arch: $!";
    my $txt = do { local $/; <$fh> };
    close $fh;
    my $original = $txt;

    # El tipo puede llevar genericos y puntos: List<String>, Integer, long,
    # LocalDateTime... Entre la anotacion y el tipo puede haber OTRAS
    # anotaciones (@DateTimeFormat, @Valid), y hay que dejarlas donde estan.
    my $ANOTS = qr/(?:\@\w+(?:\([^()]*(?:\([^()]*\)[^()]*)*\))?\s*)*/;
    my $TIPO  = qr/[A-Za-z_][\w.]*(?:<[^<>]*(?:<[^<>]*>[^<>]*)*>)?(?:\[\])?/;

    for my $anot ('RequestParam', 'PathVariable') {
        # 1) Con parentesis y atributos, pero sin name= ni value=
        $txt =~ s{
            \@$anot \( ( [^()]* (?: \([^()]*\)[^()]* )* ) \)   # $1 atributos
            (\s* $ANOTS \s*)                                    # $2 lo de en medio
            ($TIPO)                                             # $3 tipo
            (\s+)                                               # $4
            (\w+)                                               # $5 nombre
        }{
            my ($atrib, $medio, $tipo, $esp, $nom) = ($1, $2, $3, $4, $5);
            if ($atrib =~ /\b(?:name|value)\s*=/) {
                "\@$anot($atrib)$medio$tipo$esp$nom";           # ya lo tiene
            } else {
                $cambios++;
                my $nuevos = $atrib =~ /\S/ ? "name = \"$nom\", $atrib" : "name = \"$nom\"";
                "\@$anot($nuevos)$medio$tipo$esp$nom";
            }
        }gex;

        # 2) Sin parentesis: @RequestParam Integer id
        $txt =~ s{
            \@$anot (?!\s*\()
            (\s+ $ANOTS \s*)
            ($TIPO)
            (\s+)
            (\w+)
        }{
            # Sin atributos no hay grupo $1 de atributos: el nombre es el $4.
            $cambios++;
            "\@$anot(name = \"$4\")$1$2$3$4"
        }gex;
    }

    next if $txt eq $original;
    $tocados++;
    if ($revisar) {
        print "CAMBIARIA: $arch\n";
    } else {
        open(my $out, '>:encoding(UTF-8)', $arch) or die "No se pudo escribir $arch: $!";
        print $out $txt;
        close $out;
    }
}

print "$tocados fichero(s), $cambios parametro(s) nombrado(s)\n";
