package Sprocket::Util::Accessor;

use strict;
use warnings;

use Class::Accessor::Fast;
use base qw( Class::Accessor::Fast );

sub new {
    my ( $class, $self, @accessors ) = @_;
    __PACKAGE__->mk_accessors( @accessors ) if ( @accessors );
    bless( $self, ref $class || $class );
}

1;
