package Sprocket::Server::FDreceiver;

use strict;
use warnings;

use POE;
use Sprocket qw( Server Util::FDpasser );

use Socket qw( INADDR_ANY inet_ntoa inet_aton AF_INET AF_UNIX PF_UNIX sockaddr_in );

use base qw( Sprocket::Server );

sub spawn {
    my $class = shift;

    my $self = $class->SUPER::spawn(
        @_,
        FdPasser => 1
    );

    $self->{fdpasser} = Sprocket::Util::FDpasser->new(
        EndpointFile => $self->{opts}->{endpoint_file}
    );

    return $self;
}

sub check_params {
    my $self = shift;

    die "EndpointFile is mandatory for ".__PACKAGE__
        unless ( $self->{opts}->{endpoint_file} );

    return;
}

sub _startup {
    my ( $self, $kernel, $session ) = @_[ OBJECT, KERNEL, SESSION ];

    $kernel->state( 'fdpasser_accept' => $self );

    $sprocket->attach_hook(
        'sprocket.fdpasser.accept',
        $sprocket->callback( $session => 'fdpasser_accept' )
    );
    
    $self->_log( v => 2, msg => sprintf( "Listening for FDs on %s", $self->{opts}->{endpoint_file} ) );

    return;
}

sub fdpasser_accept {
    my ( $self, $kernel, $event ) = @_[ OBJECT, KERNEL, ARG0 ];

    my $socket = delete $event->{fh};

    warn "fd accept: $socket";
    my ( $port, $ip ) = ( sockaddr_in( getsockname( $socket ) ) );
    $ip = inet_ntoa( $ip );

    my $peer_ip = '127.0.0.1';
    my $peer_port = 0;
    
    my $con = $self->new_connection(
        local_ip => $ip,
        local_port => $port,
        peer_ip => $peer_ip,
        # TODO resolve these?
        peer_hostname => $peer_ip,
        peer_port => $peer_port,
        peer_addr => "$peer_ip:$peer_port",
    );
    
    $con->socket( $socket );

    $self->process_plugins( [ 'local_accept', $self, $con, $socket ] );
    
    return;
}

1;

__END__

=head1 NAME

Sprocket::Server::FDreceiver - A Sprocket Server that serves sockets passed from File::FDpasser

=head1 SYNOPSIS

    use Sprocket qw( Server::FDreceiver );

    Sprocket::Server::FDreceiver->spawn(
        Name => 'Test Server',
        Endpoint => '/tmp/sprocket',
        Plugins => [
            {
                plugin => MyPlugin->new(),
                priority => 0, # default
            },
        ],
        LogLevel => 4,
    );


=head1 DESCRIPTION

Sprocket::Server::FDreceiver serves sockets passed using File::FDpasser.

=head1 NOTE

This module subclasses L<Sprocket:Server> with one additional parameter:
Endpoint => (Path).

=head1 SEE ALSO

L<POE>, L<Sprocket>, L<Sprocket::Connection>, L<Sprocket::Plugin>,
L<Sprocket::Client>, L<Sprocket::Server>, L<File::FDpasser>

=head1 AUTHOR

David Davis E<lt>xantus@cpan.orgE<gt>

=head1 RATING

Please rate this module.
L<http://cpanratings.perl.org/rate/?distribution=Sprocket>

=head1 COPYRIGHT AND LICENSE

Copyright 2006-2007 by David Davis

See L<Sprocket> for license information.

=cut

