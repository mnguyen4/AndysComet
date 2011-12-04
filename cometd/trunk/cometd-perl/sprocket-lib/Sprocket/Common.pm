package Sprocket::Common;

use strict;
use warnings;

use Data::UUID;
use Scalar::Util qw( reftype );

our %hex_chr;
our %chr_hex;
our $super_event = 'sub super_event {
my $self = shift; my $caller = ( caller( 1 ) )[ 3 ];
$caller =~ s/.*::(.+)$/$1/; $caller= "SUPER::$caller";
my $ret = $self->$caller( @_ ); unshift( @_, $self );
push( @_, $ret ); return @_; }';

BEGIN {
    # faster uri escape/unescape
    for ( 0 .. 255 ) {
        my $h = sprintf( "%%%02X", $_ );
        my $c = chr( $_ );
        $chr_hex{ $c } = $h;
        $hex_chr{ lc( $h ) } = $hex_chr{ uc( $h ) } = $c;
    }
}

sub import {
    my ( $class, $args ) = @_;
    my $package = caller();

    my @exports = qw(
        uri_unescape
        uri_escape
        adjust_params
        gen_uuid
        new_uuid
    );

    push( @exports, @_ ) if ( @_ );
    
    no strict 'refs';
    foreach my $sub ( @exports ) {
        if ( $sub eq 'super_event' ) {
            # XXX We must define this sub in the class because it uses SUPER
            # I don't know of any other way to do this, yet.
            eval ( "package $package;" . $super_event )
                if ( !defined *{ $package . '::super_event' } );
        } else {
            *{ $package . '::' . $sub } = \&$sub;
        }
    }
}

sub uri_escape {
    my $es = shift or return;
    $es =~ s/([^A-Za-z0-9\-_.!~*'()])/$chr_hex{$1}||_try_utf8($1)/ge;
    return $es;
}

sub _try_utf8 {
    my $c = eval { utf8::encode( shift ); };
    if ( $@ ) {
        warn $@;
        return '';
    }
    return $c;
}

sub uri_unescape {
    my $es = shift or return;
    $es =~ tr/+/ /; # foo=this+is+a+test
    $es =~ s/(%[0-9a-fA-F]{2})/$hex_chr{$1}/gs;
    return $es;
}

# ThisIsCamelCase -> this_is_camel_case
# my %opts = &adjust_params;
# my $t = adjust_params($f or @_ ); # $f being a hashref
sub adjust_params {
    my $o = ( $#_ == 0 && isa_HASH( $_[ 0 ] ) ) ? shift : { @_ };
    foreach my $k ( keys %$o ) {
        local $_ = "$k";
        s/([A-Z][a-z]+)/lc($1)."_"/ge; s/_$//;
        $o->{+lc} = delete $o->{$k};
    }
    return wantarray ? %$o : $o;
}

sub gen_uuid {
    my $from = shift;
    my $u = Data::UUID->new();
    my $uuid = $u->create_from_name( "cc.sprocket", "$from" );
    return lc( $u->to_string( $uuid ) );
}

sub new_uuid {
    return lc( new Data::UUID->create_str() );
}

sub isa_HASH {
    my $rt = reftype( $_[ 0 ] );
    return ( $rt && $rt eq 'HASH' ) ? 1 : undef;
}

sub isa_ARRAY {
    my $rt = reftype( $_[ 0 ] );
    return ( $rt && $rt eq 'ARRAY' ) ? 1 : undef;
}

sub isa_CODE {
    my $rt = reftype( $_[ 0 ] );
    return ( $rt && $rt eq 'CODE' ) ? 1 : undef;
}

sub isa_SCALAR {
    my $rt = reftype( $_[ 0 ] );
    return ( $rt && $rt eq 'SCALAR' ) ? 1 : undef;
}

sub drop_privs {
    my ( $uid, $gid ) = @_;

    die "You must specify at least a user/uid to drop_privs"
        unless ( defined ( $uid ) );

    # not a number, resolve by user name
    if ( $uid !~ m/^\d+$/ ) {
        my $u = getpwnam( $uid );
        die "unknown user: $uid! You could try using the uid instead"
            unless( defined( $u ) );
        $uid = $u;
    }

    # not a number, resolve by group name
    if ( defined( $gid ) && $gid !~ m/^\d+$/ ) {
        my $g = getgrnam( $gid );
        die "unknown group: $gid! You could try using the gid instead"
            unless( defined( $g ) );
        $gid = $g;
    }
    
    # drop the group first, then the user
    _set_egid( $gid ) if defined( $gid );
    _set_euid( $uid ) if ( $uid );

    return ( $uid, $gid );
}

sub _set_euid {
    $> = $_[ 0 ];
    die "could not set the effective uid: $_[ 0 ]" unless( $_[ 0 ] == $> );
}

sub _set_egid {
    $) = $_[ 0 ];
    die "could not set the effective gid: $_[ 0 ]" unless( $_[ 0 ] == $) );
}

1;
